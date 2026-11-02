from contextlib import redirect_stdout
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from test_release import release


class PublishContractTest(unittest.TestCase):
    def prepare(self, root):
        data = release.metadata()
        notes = root / data['notes']
        notes.parent.mkdir(parents=True)
        notes.write_text('Release notes for the publication fixture.\n')
        destination = root / 'dist/release'
        destination.mkdir(parents=True)
        records = []
        for flavor in release.FLAVORS:
            name = f"FlexiList-{data['version']}-{flavor.lower()}-release.apk"
            path = destination / name
            path.write_bytes(flavor.encode())
            records.append(dict(name=name, size=path.stat().st_size, sha256=release.digest(path), kind='release', signed=True))
        manifest = dict(source_sha='a' * 40, version=data['version'], assets=records)
        (destination / 'release-manifest.json').write_text(json.dumps(manifest))
        (destination / 'SHA256SUMS.txt').write_text(''.join(f'{release.digest(p)}  {p.name}\n' for p in sorted(destination.iterdir())))
        remote = [dict(name=p.name, size=p.stat().st_size, digest='sha256:' + release.digest(p)) for p in destination.iterdir()]
        return data, remote

    def test_only_matching_remote_assets_allow_draft_publication(self):
        for corrupt in (False, True):
            with self.subTest(corrupt=corrupt), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                data, remote = self.prepare(root)
                if corrupt:
                    remote[0]['digest'] = 'sha256:' + '0' * 64
                responses = [[], {'id': 42}, [], remote]
                final = dict(draft=False, tag_name=data['tag'], html_url='https://github.com/example/release')
                with patch.object(release, 'ROOT', root), patch.object(release, 'metadata', return_value=data), \
                     patch.dict(os.environ, GH_TOKEN='test-fixture', GITHUB_REPOSITORY='example/repository'), \
                     patch.object(release, 'api', side_effect=responses), \
                     patch.object(release, 'urlopen', side_effect=lambda *a, **k: io.BytesIO(b'{}')), \
                     patch.object(release.subprocess, 'check_call'), \
                     patch.object(release.subprocess, 'check_output', return_value=json.dumps(final)) as finalize, \
                     redirect_stdout(io.StringIO()):
                    if corrupt:
                        with self.assertRaisesRegex(AssertionError, 'digest mismatch'):
                            release.publish('a' * 40)
                        finalize.assert_not_called()
                    else:
                        release.publish('a' * 40)
                        finalize.assert_called_once()
                        self.assertIn('PATCH', finalize.call_args.args[0])

    def test_existing_public_release_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data, remote = self.prepare(root)
            with patch.object(release, 'ROOT', root), patch.object(release, 'metadata', return_value=data), \
                 patch.dict(os.environ, GITHUB_REPOSITORY='example/repository'), \
                 patch.object(release, 'api', return_value=[dict(tag_name=data['tag'], draft=False)]), \
                 patch.object(release.subprocess, 'check_call'), patch.object(release, 'urlopen') as upload:
                with self.assertRaisesRegex(AssertionError, 'already published'):
                    release.publish('a' * 40)
                upload.assert_not_called()
