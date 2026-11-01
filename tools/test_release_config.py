"""Static fail-closed release configuration gates; no signing secrets needed."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

class ReleaseConfiguration(unittest.TestCase):
    def test_optional_online_provisioning_version_and_permission(self):
        import xml.etree.ElementTree as ET
        build = (ROOT / 'app/build.gradle').read_text()
        self.assertIn("versionCode 6", build)
        self.assertIn("versionName '1.1.1'", build)
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
        internet = [x for x in manifest.findall('uses-permission') if x.get('{http://schemas.android.com/apk/res/android}name') == 'android.permission.INTERNET']
        self.assertEqual(len(internet), 1)
        self.assertNotEqual(internet[0].get('{http://schemas.android.com/tools}node'), 'remove')

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
        self.assertIn('verify_release_apk.py', workflow)
        self.assertIn('if-no-files-found: error', workflow)

    def test_tag_release_has_trust_and_publication_gates(self):
        workflow = (ROOT / '.github/workflows/android.yml').read_text()
        self.assertIn("tags: ['v*']", workflow)
        self.assertIn("github.event_name == 'push'", workflow)
        self.assertIn('fetch-depth: 0', workflow)
        self.assertIn('refs/heads/main:refs/remotes/origin/main', workflow)
        self.assertIn('python3 -B tools/release_guard.py', workflow)
        self.assertLess(workflow.index('tools/release_guard.py'), workflow.index('secrets.ANDROID_RELEASE_KEYSTORE_BASE64'))
        self.assertIn('gh release create "$RELEASE_TAG"', workflow)
        self.assertIn('--verify-tag', workflow)
        self.assertIn('--draft=false --prerelease=false', workflow)
        self.assertIn('--notes-file "docs/releases/${RELEASE_TAG}.fa.md"', workflow)
        self.assertIn('gh release view', workflow)
        self.assertIn('gh release download', workflow)
        self.assertIn('(cd dist && sha256sum btc-tag-mapper.apk signing-certificate.txt > SHA256SUMS)', workflow)
        self.assertEqual(workflow.count('contents: write'), 1)
        self.assertIn('python3 -B -m unittest discover -s tools', workflow)
        self.assertIn('tools/verify_scanner_runtime.py', workflow)
        self.assertIn('trap', workflow)
        self.assertIn('umask 077', workflow)
        self.assertIn('if: always()', workflow)

if __name__ == '__main__':
    unittest.main()
