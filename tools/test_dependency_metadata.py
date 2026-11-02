"""Regression for module metadata required by a fresh hosted resolver."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {'5.9.2': 'ab137ba5a8e32c9b066bf9126a1c76dd5614b724ba5c0b02549772b5e9f4cf1f', '5.9.3': 'b401fd25901e582a524aa5343c4b39e28bc56e24961c1069bf2b4bbfcee46b93'}
class DependencyMetadata(unittest.TestCase):
    def test_junit_bom_module_checksums_are_pinned(self):
        tree = ET.parse(ROOT / 'gradle/verification-metadata.xml')
        ns = {'v': 'https://schema.gradle.org/dependency-verification'}
        for version, digest in EXPECTED.items():
            component = tree.find(f".//v:component[@group='org.junit'][@name='junit-bom'][@version='{version}']", ns)
            self.assertIsNotNone(component)
            checksum = component.find(f"v:artifact[@name='junit-bom-{version}.module']/v:sha256", ns)
            self.assertIsNotNone(checksum)
            self.assertEqual(checksum.get('value'), digest)
