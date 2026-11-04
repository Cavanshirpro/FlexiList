#!/usr/bin/env python3
"""Generate the F-Droid recipe for a pinned FlexiList source revision."""
import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP_ID = 'xyz.younglion.flexilist'


def render(commit, root=ROOT):
    if not re.fullmatch('[0-9a-f]{40}', commit):
        raise ValueError('F-Droid build recipes must pin a full commit SHA')
    data = json.loads((root / 'release/release.json').read_text())
    # UpdateCheckData reads the canonical JSON at each tag, rather than parsing Gradle expressions.
    return f'''Categories:
  - Habit Tracker
  - Shopping List
  - Task
  - Workout
License: GPL-3.0-only
AuthorName: Cavanshirpro
SourceCode: https://github.com/Cavanshirpro/FlexiList
IssueTracker: https://github.com/Cavanshirpro/FlexiList/issues

RepoType: git
Repo: https://github.com/Cavanshirpro/FlexiList.git
Binaries: https://github.com/Cavanshirpro/FlexiList/releases/download/v%v/FlexiList-%v-fdroid-release.apk

Builds:
  - versionName: {data['version']}
    versionCode: {data['version_code']}
    commit: {commit}
    subdir: app
    sudo: update-java-alternatives --set java-1.21.0-openjdk-amd64
    gradle:
      - fdroid

AllowedAPKSigningKeys: {data['signing_cert_sha256']}

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags ^v[0-9]+\\.[0-9]+\\.[0-9]+$
UpdateCheckData: release/release.json|"version_code":\\s*(\\d+)|.|"version":\\s*"([^\"]+)"
CurrentVersion: {data['version']}
CurrentVersionCode: {data['version_code']}
'''


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    content = render(args.commit)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(content, encoding='utf-8')
    else:
        print(content, end='')
