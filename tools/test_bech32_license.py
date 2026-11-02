"""The distributed notice must retain the exact upstream bytes."""
from pathlib import Path
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def validate_notice(archive):
    if archive.read('assets/BECH32-LICENSE.txt') != (ROOT / 'core/BECH32-LICENSE.txt').read_bytes():
        raise ValueError('Packaged Bech32 notice differs from upstream')


class Bech32License(unittest.TestCase):
    def test_distributed_notice_matches_upstream(self):
        asset = ROOT / 'app/src/main/assets/BECH32-LICENSE.txt'
        self.assertTrue(asset.is_file(), 'Bech32 distribution notice missing')
        self.assertEqual(asset.read_bytes(), (ROOT / 'core/BECH32-LICENSE.txt').read_bytes())


if __name__ == '__main__':
    import sys
    if len(sys.argv) == 2 and sys.argv[1].endswith('.apk'):
        with zipfile.ZipFile(sys.argv[1]) as archive:
            validate_notice(archive)
        print('PASS: APK Bech32 notice byte equality')
    else:
        unittest.main()
