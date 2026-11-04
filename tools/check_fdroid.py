#!/usr/bin/env python3
"""Check the generated F-Droid recipe with the upstream linter and validate store assets."""
import argparse
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from urllib.request import urlopen

from PIL import Image
import yaml

from fdroid_metadata import APP_ID, ROOT, render


def main(commit):
    with Image.open(ROOT / 'fastlane/metadata/android/en-US/images/icon.png') as image:
        assert image.format == 'PNG' and image.size == (512, 512), 'Store icon must be a valid 512px PNG'
        image.verify()
    for locale in ('en-US', 'tr-TR'):
        base = ROOT / 'fastlane/metadata/android' / locale
        for name in ('title.txt', 'short_description.txt', 'full_description.txt'):
            assert (base / name).read_text().strip(), f'Missing store metadata: {locale}/{name}'
    url = 'https://gitlab.com/fdroid/fdroiddata/-/raw/master/config/categories.yml'
    with urlopen(url, timeout=30) as response:
        categories = yaml.safe_load(response)
    with tempfile.TemporaryDirectory(prefix='flexilist-fdroid-') as directory:
        root = Path(directory)
        (root / 'metadata').mkdir()
        (root / 'metadata' / f'{APP_ID}.yml').write_text(render(commit))
        # Category names come from fdroiddata; category artwork is not needed to lint one recipe.
        (root / 'config.yml').write_text(yaml.safe_dump({'categories': list(categories)}))
        executable = shutil.which('fdroid') or str(Path(sys.executable).with_name('fdroid'))
        subprocess.run([executable, 'lint', '--format', APP_ID], cwd=root, check=True)
    print('F-Droid metadata, update matching and store assets validated.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--commit', required=True)
    args = parser.parse_args()
    main(args.commit)
