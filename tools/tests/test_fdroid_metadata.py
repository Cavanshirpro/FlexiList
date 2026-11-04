import importlib.util
import json
from pathlib import Path
import re
import unittest

spec = importlib.util.spec_from_file_location('fdroid_recipe', Path(__file__).parents[1] / 'fdroid_metadata.py')
recipe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recipe)


class FdroidMetadataTest(unittest.TestCase):
    def test_recipe_is_pinned_to_source_and_published_apk(self):
        text = recipe.render('a' * 40)
        self.assertIn('commit: ' + 'a' * 40, text)
        self.assertIn('FlexiList-%v-fdroid-release.apk', text)
        self.assertIn('java-1.21.0-openjdk-amd64', text)
        self.assertNotIn('\r', text)
        self.assertNotRegex(text, r'(?m)^\s*\* ')

    def test_update_check_extracts_canonical_versions_at_tags(self):
        data = json.loads((recipe.ROOT / 'release/release.json').read_text())
        text = recipe.render('a' * 40)
        line = next(line for line in text.splitlines() if line.startswith('UpdateCheckData: '))
        path, code_pattern, version_path, version_pattern = line.removeprefix('UpdateCheckData: ').split('|')
        self.assertEqual(path, 'release/release.json')
        self.assertEqual(version_path, '.')
        content = json.dumps(data, indent=2)
        self.assertEqual(int(re.search(code_pattern, content)[1]), data['version_code'])
        self.assertEqual(re.search(version_pattern, content)[1], data['version'])

    def test_branch_or_short_hash_cannot_be_used_as_pinned_build(self):
        for commit in ('main', 'v1.0.4', 'a' * 7):
            with self.assertRaises(ValueError):
                recipe.render(commit)
