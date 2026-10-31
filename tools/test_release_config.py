"""Static fail-closed release configuration gates; no signing secrets needed."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

class ReleaseConfiguration(unittest.TestCase):
    def test_release_requires_explicit_signing(self):
        build = (ROOT / 'app/build.gradle').read_text()
        for name in ['ANDROID_RELEASE_KEYSTORE', 'ANDROID_RELEASE_STORE_PASSWORD',
                     'ANDROID_RELEASE_KEY_ALIAS', 'ANDROID_RELEASE_KEY_PASSWORD']:
            self.assertIn(name, build)
        self.assertIn('verifyReleaseSigning', build)
        self.assertIn('signingConfig signingConfigs.release', build)

    def test_workflow_signing_is_manual_trusted_and_separate(self):
        path = ROOT / '.github/workflows/android.yml'
        self.assertTrue(path.exists(), 'Android workflow missing')
        workflow = path.read_text()
        self.assertIn('workflow_dispatch:', workflow)
        self.assertIn("github.event_name == 'workflow_dispatch'", workflow)
        self.assertIn("github.ref == 'refs/heads/main'", workflow)
        self.assertIn('needs: test', workflow)
        self.assertIn('secrets.ANDROID_RELEASE_KEYSTORE_BASE64', workflow)
        self.assertIn('if: always()', workflow)
        self.assertNotIn('pull_request_target:', workflow)
        self.assertIn('apksigner', workflow)
        self.assertIn('if-no-files-found: error', workflow)

if __name__ == '__main__':
    unittest.main()
