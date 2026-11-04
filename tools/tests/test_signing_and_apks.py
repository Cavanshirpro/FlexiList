from contextlib import redirect_stdout
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import BadZipFile, ZipFile

from test_release import release


class SigningConfigurationTest(unittest.TestCase):
    def test_unsigned_validation_is_allowed_without_credentials(self):
        with patch.dict(os.environ, {}, clear=True), redirect_stdout(io.StringIO()):
            self.assertEqual(release.signing_configuration(False)['sign_release'], 'false')

    def test_publication_requires_all_signing_credentials(self):
        for values in ({}, {'KEYSTORE_BASE64': 'fixture'}, {'STORE_PASSWORD': 'fixture', 'KEY_PASSWORD': 'fixture'}):
            with self.subTest(values=list(values)), patch.dict(os.environ, values, clear=True):
                with self.assertRaisesRegex(ValueError, 'Signed publication requires'):
                    release.signing_configuration(True)

    def test_complete_credentials_enable_signed_candidates(self):
        values = {key: 'fixture' for key in ('KEYSTORE_BASE64', 'STORE_PASSWORD', 'KEY_PASSWORD')}
        with patch.dict(os.environ, values, clear=True), redirect_stdout(io.StringIO()):
            self.assertEqual(release.signing_configuration(True)['sign_release'], 'true')


class ApkArchiveTest(unittest.TestCase):
    def test_corrupt_archive_is_rejected_before_inspection(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'corrupt.apk'
            apk.write_bytes(b'PK\x03\x04broken file')
            with self.assertRaises(BadZipFile):
                release.inspect_apk(apk, {}, False)

    def test_missing_app_files_and_dependency_blocks_are_rejected(self):
        for files, message in ((['classes.dex'], 'Incomplete APK'),
                               (['AndroidManifest.xml', 'classes.dex', 'META-INF/com/android/build/gradle/dependencies.pb'], 'dependency metadata')):
            with self.subTest(files=files), tempfile.TemporaryDirectory() as directory:
                apk = Path(directory) / 'candidate.apk'
                with ZipFile(apk, 'w') as archive:
                    for name in files:
                        archive.writestr(name, b'fixture')
                with self.assertRaisesRegex(AssertionError, message):
                    release.inspect_apk(apk, {}, False)
