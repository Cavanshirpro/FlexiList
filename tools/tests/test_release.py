import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('release', Path(__file__).parents[1] / 'release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseContractTest(unittest.TestCase):
    def test_repository_metadata(self):
        self.assertEqual(release.metadata()['tag'], 'v' + release.metadata()['version'])

    def candidate(self, root):
        data = release.metadata()
        records = []
        for kind in ('debug', 'release'):
            name = f"FlexiList-{data['version']}-full-{kind}.apk"
            path = root / name
            path.write_bytes(kind.encode())
            records.append(dict(name=name, size=path.stat().st_size, sha256=release.digest(path), kind=kind, signed=True))
        manifest = dict(source_sha='a' * 40, version=data['version'], version_code=data['version_code'], flavor='Full', assets=records)
        (root / 'manifest.json').write_text(json.dumps(manifest))
        return data, manifest

    def test_exact_signed_candidate_is_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data, manifest = self.candidate(root)
            self.assertEqual(release.verify_candidate(root, 'Full', 'a' * 40, data)['kind'], 'release')

    def test_changed_apk_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data, manifest = self.candidate(root)
            (root / manifest['assets'][0]['name']).write_bytes(b'corrupted')
            with self.assertRaises(AssertionError):
                release.verify_candidate(root, 'Full', 'a' * 40, data)

    def test_other_source_commit_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data, manifest = self.candidate(root)
            with self.assertRaises(AssertionError):
                release.verify_candidate(root, 'Full', 'b' * 40, data)

    def test_unsigned_or_incomplete_candidate_is_rejected(self):
        for defect in ('unsigned', 'missing', 'extra', 'wrong_version', 'path'):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                data, manifest = self.candidate(root)
                if defect == 'unsigned':
                    manifest['assets'][1]['signed'] = False
                elif defect == 'missing':
                    manifest['assets'].pop()
                elif defect == 'extra':
                    (root / 'old.apk').write_bytes(b'stale')
                elif defect == 'wrong_version':
                    manifest['version_code'] += 1
                else:
                    manifest['assets'][0]['name'] = '../escape.apk'
                (root / 'manifest.json').write_text(json.dumps(manifest))
                with self.assertRaises(AssertionError):
                    release.verify_candidate(root, 'Full', 'a' * 40, data)

    def test_unsigned_release_is_only_accepted_in_validation_mode(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data, manifest = self.candidate(root)
            record = manifest['assets'][1]
            old_name = record['name']
            record['name'] = old_name.removesuffix('.apk') + '-unsigned.apk'
            record['signed'] = False
            (root / old_name).rename(root / record['name'])
            (root / 'manifest.json').write_text(json.dumps(manifest))
            with self.assertRaisesRegex(AssertionError, 'Unsigned'):
                release.verify_candidate(root, 'Full', 'a' * 40, data)
            result = release.verify_candidate(root, 'Full', 'a' * 40, data, require_signed=False)
            self.assertFalse(result['signed'])
            manifest['assets'][0]['signed'] = False
            (root / 'manifest.json').write_text(json.dumps(manifest))
            with self.assertRaisesRegex(AssertionError, 'Unsigned'):
                release.verify_candidate(root, 'Full', 'a' * 40, data, require_signed=False)

    def test_unsafe_notes_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'release').mkdir()
            data = release.metadata()
            data['notes'] = '../notes.md'
            (root / 'release/release.json').write_text(json.dumps(data))
            with self.assertRaisesRegex(AssertionError, 'Unsafe'):
                release.metadata(root)

    def test_failed_candidate_does_not_create_publish_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(release, 'ROOT', root), patch.object(release, 'metadata', return_value=release.metadata()):
                with self.assertRaises(FileNotFoundError):
                    release.collect(root / 'candidates', 'a' * 40)
                self.assertFalse((root / 'dist/release').exists())


if __name__ == '__main__':
    unittest.main()
