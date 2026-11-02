from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

RES = Path(__file__).resolve().parents[2] / 'app/src/main/res'


class TranslationContractTest(unittest.TestCase):
    def test_all_supported_languages_have_unique_keys_and_matching_arguments(self):
        def strings(path):
            entries = ET.parse(path).getroot().findall('string')
            result = {e.attrib['name']: e.text or '' for e in entries}
            self.assertEqual(len(entries), len(result), f'Duplicate strings in {path}')
            return result

        base = strings(RES / 'values/strings.xml')
        for lang in ('tr', 'ru', 'de', 'es', 'fr'):
            with self.subTest(language=lang):
                localized = strings(RES / f'values-{lang}/strings.xml')
                self.assertEqual(set(base), set(localized), f'Missing translation keys in {lang}')
                for key, value in base.items():
                    expected = sorted(re.findall(r'%\d+\$[dsf]', value))
                    actual = sorted(re.findall(r'%\d+\$[dsf]', localized[key]))
                    self.assertEqual(expected, actual, f'Format arguments differ: {lang}/{key}')


if __name__ == '__main__':
    unittest.main()
