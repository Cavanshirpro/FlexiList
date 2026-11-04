#!/usr/bin/env python3
"""Build/release contract. All publication uses artifacts from this workflow's commit."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from zipfile import ZipFile

from urllib.parse import quote
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
FLAVORS = ('Full', 'Fdroid')


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def metadata(root=ROOT):
    data = json.loads((root / 'release/release.json').read_text())
    assert re.fullmatch(r'\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?', data['version']), 'Invalid version'
    assert type(data['version_code']) is int and data['version_code'] > 0, 'Invalid version code'
    assert data['tag'] == 'v' + data['version'], 'Tag/version mismatch'
    assert isinstance(data['title'], str) and '\n' not in data['title'], 'Invalid title'
    assert type(data['prerelease']) is bool, 'Invalid prerelease flag'
    assert re.fullmatch('[0-9a-f]{64}', data['signing_cert_sha256']), 'Invalid signing certificate'
    notes = Path(data['notes'])
    assert not notes.is_absolute() and '..' not in notes.parts, 'Unsafe release notes path'
    assert (root / notes).read_text().strip(), 'Empty release notes'
    return data


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def api(endpoint, payload=None, **kwargs):
    args = ['gh', 'api', endpoint]
    if payload is not None:
        args += ['--method', 'POST', '--input', '-']
        kwargs['input'] = json.dumps(payload)
    return json.loads(subprocess.check_output(args, text=True, **kwargs))


def output(values):
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as stream:
            for key, value in values.items():
                stream.write(f'{key}={value}\n')
    print(json.dumps(values, indent=2))


def signing_configuration(publish_requested):
    required = ('KEYSTORE_BASE64', 'STORE_PASSWORD', 'KEY_PASSWORD')
    missing = [name for name in required if not os.environ.get(name, '').strip()]
    if publish_requested and missing:
        raise ValueError('Signed publication requires repository Actions secrets: ' + ', '.join(missing)
                         + '. Use the existing release keystore; see docs/RELEASING.md.')
    result = {'sign_release': 'false' if missing else 'true'}
    output(result)
    if missing:
        print('Validation will inspect unsigned release candidates. No public release will be created.')
    return result


def preflight():
    data = metadata()
    ref = os.environ['GITHUB_REF']
    sha = os.environ['GITHUB_SHA']
    assert run('git', 'rev-parse', 'HEAD') == sha, 'Checkout/source mismatch'
    if ref.startswith('refs/tags/'):
        assert ref == 'refs/tags/' + data['tag'], 'Pushed tag/version mismatch'
    else:
        assert ref == 'refs/heads/main', 'Manual releases must run from main'
        branch = api(f"repos/{os.environ['GITHUB_REPOSITORY']}/branches/main")
        assert branch['commit']['sha'] == sha, 'main advanced; run again'
    subprocess.check_call(['git', 'merge-base', '--is-ancestor', sha, 'origin/main'])
    output({'tag': data['tag'], 'version': data['version'], 'source_sha': sha})


def inspect_apk(apk, data, signed):
    with ZipFile(apk) as archive:
        assert archive.testzip() is None, 'Corrupt APK ZIP entry'
        assert 'AndroidManifest.xml' in archive.namelist() and 'classes.dex' in archive.namelist(), 'Incomplete APK archive'
        assert 'META-INF/com/android/build/gradle/dependencies.pb' not in archive.namelist(), 'APK contains dependency metadata'
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    analyzer = Path(shutil.which('apkanalyzer') or sdk / 'cmdline-tools/latest/bin/apkanalyzer')
    for key, expected in [('application-id', 'xyz.younglion.flexilist'),
                          ('version-name', data['version']), ('version-code', str(data['version_code']))]:
        assert run(str(analyzer), 'manifest', key, str(apk)) == expected, f'APK {key} mismatch'
    if signed:
        result = run(str(sdk / 'build-tools/36.0.0/apksigner'), 'verify', '--print-certs', str(apk))
        cert = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)', result)
        assert cert and cert[1].lower() == data['signing_cert_sha256'], 'Release signing certificate mismatch'


def stage(flavor, sha):
    data = metadata()
    assert flavor in FLAVORS
    assert re.fullmatch('[0-9a-f]{40}', sha), 'Invalid source SHA'
    signed = os.environ.get('SIGNED_RELEASE') == 'true'
    destination = ROOT / 'dist' / flavor
    destination.mkdir(parents=True, exist_ok=True)
    records = []
    for kind in ('debug', 'release'):
        apks = list((ROOT / 'app/build/outputs/apk' / flavor.lower() / kind).glob('*.apk'))
        assert len(apks) == 1, f'Expected one {flavor} {kind} APK, found {len(apks)}'
        inspect_apk(apks[0], data, kind == 'release' and signed)
        suffix = '-unsigned' if kind == 'release' and not signed else ''
        name = f"FlexiList-{data['version']}-{flavor.lower()}-{kind}{suffix}.apk"
        target = destination / name
        shutil.copyfile(apks[0], target)
        records.append({'name': name, 'sha256': digest(target), 'size': target.stat().st_size,
                        'kind': kind, 'signed': kind == 'debug' or signed})
    manifest = {'version': data['version'], 'version_code': data['version_code'], 'source_sha': sha,
                'flavor': flavor, 'assets': records}
    (destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


def verify_candidate(directory, flavor, sha, data, require_signed=True):
    manifest = json.loads((directory / 'manifest.json').read_text())
    assert manifest['source_sha'] == sha, 'Candidate source mismatch'
    assert manifest['version'] == data['version'] and manifest['version_code'] == data['version_code'], 'Candidate version mismatch'
    assert manifest['flavor'] == flavor, 'Candidate flavor mismatch'
    records = manifest['assets']
    assert len(records) == 2 and {r['kind'] for r in records} == {'debug', 'release'}, 'Incomplete artifact set'
    expected = {'manifest.json'}
    for record in records:
        assert type(record['signed']) is bool, 'Invalid signing status'
        assert record['signed'] or (not require_signed and record['kind'] == 'release'), 'Unsigned candidate'
        suffix = '-unsigned' if not record['signed'] else ''
        name = f"FlexiList-{data['version']}-{flavor.lower()}-{record['kind']}{suffix}.apk"
        assert record['name'] == name, 'Misnamed candidate'
        apk = directory / name
        assert apk.stat().st_size == record['size'] and digest(apk) == record['sha256'], 'Artifact checksum mismatch'
        expected.add(name)
    assert {p.name for p in directory.iterdir()} == expected, 'Unexpected artifact files'
    return next(r for r in records if r['kind'] == 'release')


def collect(source, sha, allow_unsigned=False):
    data = metadata()
    destination = ROOT / 'dist/release'
    assert not destination.exists(), 'Release staging directory already exists'
    selected = []
    for flavor in FLAVORS:
        directory = source / ('android-' + flavor)
        selected.append((directory, verify_candidate(directory, flavor, sha, data, require_signed=not allow_unsigned)))
    destination.mkdir(parents=True)
    assets = []
    for directory, record in selected:
        shutil.copyfile(directory / record['name'], destination / record['name'])
        assets.append(record)
    manifest = {'source_sha': sha, 'version': data['version'], 'assets': assets}
    (destination / 'release-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    checksums = ''.join(f"{digest(p)}  {p.name}\n" for p in sorted(destination.iterdir()))
    (destination / 'SHA256SUMS.txt').write_text(checksums)


def publish(sha):
    data = metadata()
    source = ROOT / 'dist/release'
    manifest = json.loads((source / 'release-manifest.json').read_text())
    assert manifest['source_sha'] == sha and manifest['version'] == data['version'], 'Staged source/version mismatch'
    expected_apks = {f"FlexiList-{data['version']}-{flavor.lower()}-release.apk" for flavor in FLAVORS}
    assert len(manifest['assets']) == 2 and {r['name'] for r in manifest['assets']} == expected_apks, 'Incomplete release'
    assert {p.name for p in source.iterdir()} == expected_apks | {'release-manifest.json', 'SHA256SUMS.txt'}, 'Unexpected staged files'
    for record in manifest['assets']:
        assert record['signed'] and digest(source / record['name']) == record['sha256'], 'Invalid release APK'
    subprocess.check_call(['sha256sum', '--check', 'SHA256SUMS.txt'], cwd=source)
    repo = os.environ['GITHUB_REPOSITORY']
    # Never replace an existing public release or an unrelated draft.
    releases = api(f'repos/{repo}/releases?per_page=100')
    matches = [r for r in releases if r['tag_name'] == data['tag']]
    assert len(matches) <= 1, 'Ambiguous existing drafts'
    for release in matches:
        assert release['draft'] and release['target_commitish'] == sha, 'Release already published or targets another commit'
    body = (ROOT / data['notes']).read_text() + f'\nSource commit: `{sha}`\n\nSHA-256:\n```text\n' + (source / 'SHA256SUMS.txt').read_text() + '```\n'
    release = matches[0] if matches else api(f'repos/{repo}/releases', {
        'tag_name': data['tag'], 'target_commitish': sha, 'name': data['title'],
        'body': body, 'draft': True, 'prerelease': data['prerelease']})
    release_id = release['id']
    files = sorted(source.iterdir())
    remote_assets = api(f'repos/{repo}/releases/{release_id}/assets?per_page=100')
    for asset in remote_assets:
        subprocess.check_call(['gh', 'api', '--method', 'DELETE', f"repos/{repo}/releases/assets/{asset['id']}"])
    for path in files:
        request = Request(
            f'https://uploads.github.com/repos/{repo}/releases/{release_id}/assets?name={quote(path.name)}',
            data=path.read_bytes(), method='POST', headers={
                'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
                'Accept': 'application/vnd.github+json',
                'Content-Type': 'application/octet-stream',
                'X-GitHub-Api-Version': '2022-11-28',
            })
        with urlopen(request, timeout=120) as response:
            json.load(response)
    remote_assets = api(f'repos/{repo}/releases/{release_id}/assets?per_page=100')
    assert {a['name'] for a in remote_assets} == {p.name for p in files}, 'Uploaded asset set mismatch'
    for asset in remote_assets:
        local = source / asset['name']
        assert asset['size'] == local.stat().st_size, 'Uploaded asset size mismatch'
        assert asset.get('digest') == 'sha256:' + digest(local), 'Uploaded asset digest mismatch'
    result = json.loads(subprocess.check_output(['gh', 'api', '--method', 'PATCH',
        f'repos/{repo}/releases/{release_id}', '--input', '-'],
        input=json.dumps({'draft': False, 'body': body}), text=True))
    assert not result['draft'] and result['tag_name'] == data['tag'], 'Publication failed'
    print(result['html_url'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('metadata', 'signing', 'preflight', 'stage', 'collect', 'publish'))
    parser.add_argument('--flavor', choices=FLAVORS)
    parser.add_argument('--sha')
    parser.add_argument('--source', type=Path)
    parser.add_argument('--allow-unsigned', action='store_true', help='Validation only; publication still requires signed APKs')
    args = parser.parse_args()
    os.chdir(ROOT)
    if args.command == 'metadata':
        output(metadata())
    elif args.command == 'signing':
        signing_configuration(os.environ.get('PUBLISH_REQUESTED') == 'true')
    elif args.command == 'preflight':
        preflight()
    elif args.command == 'stage':
        stage(args.flavor, args.sha)
    elif args.command == 'collect':
        collect(args.source, args.sha, allow_unsigned=args.allow_unsigned)
    else:
        publish(args.sha)
