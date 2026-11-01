"""Policy fixtures are synthetic; these tests do not claim an APK was signed."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('verify_release_apk', ROOT / 'tools/verify_release_apk.py')
verify = None
if (ROOT / 'tools/verify_release_apk.py').exists():
    verify = importlib.util.module_from_spec(SPEC)
    SPEC.loader.exec_module(verify)

CERT = '7c4ccefc998b2407b22c2a7a3bc97e9b5f9baca3089886a5e71dcfef85b0fa3b'
BADGING = """package: name='com.saebm.btcnfcseller' versionCode='3' versionName='1.0.0' platformBuildVersionName='15'
uses-permission: name='android.permission.NFC'
uses-permission: name='android.permission.CAMERA'
uses-permission: name='com.saebm.btcnfcseller.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
"""
SIGNER = 'Signer #1 certificate SHA-256 digest: ' + CERT + '\n'


class ArtifactPolicy(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(verify, 'Missing executable APK policy gate')
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.apk = Path(self.tmp.name) / 'fixture.apk'
        with zipfile.ZipFile(self.apk, 'w') as out:
            out.writestr('AndroidManifest.xml', b'fixture')

    def check(self, badging=BADGING, signer=SIGNER):
        return verify.validate_apk(self.apk, badging, signer, '1.0.0', 3)

    def test_exact_policy_passes(self):
        self.check()

    def test_package_version_and_debug_rejected(self):
        for badging in [BADGING.replace("name='com.saebm.btcnfcseller'", "name='other'"),
                        BADGING.replace("versionCode='3'", "versionCode='2'"),
                        BADGING.replace("versionName='1.0.0'", "versionName='1.0.1'"),
                        BADGING + 'application-debuggable\n']:
            with self.subTest(badging=badging), self.assertRaises(ValueError):
                self.check(badging=badging)

    def test_permission_addition_or_omission_rejected(self):
        for badging in [BADGING + "uses-permission: name='android.permission.INTERNET'\n",
                        BADGING.replace("uses-permission: name='android.permission.CAMERA'\n", ''),
                        BADGING + "uses-permission-sdk-23: name='android.permission.READ_CONTACTS'\n"]:
            with self.subTest(badging=badging), self.assertRaises(ValueError):
                self.check(badging=badging)

    def test_wrong_missing_or_multiple_signers_rejected(self):
        for signer in ['', SIGNER.replace(CERT, '0' * 64), SIGNER + SIGNER.replace('#1', '#2')]:
            with self.subTest(signer=signer), self.assertRaises(ValueError):
                self.check(signer=signer)

    def test_corrupt_archive_rejected(self):
        self.apk.write_bytes(b'not an APK')
        with self.assertRaises(ValueError):
            self.check()


if __name__ == '__main__':
    unittest.main()
