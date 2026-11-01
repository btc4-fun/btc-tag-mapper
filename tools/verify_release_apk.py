"""Verify release APK using real SDK signature/badging tools before distribution."""
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

EXPECTED_CERT = '7c4ccefc998b2407b22c2a7a3bc97e9b5f9baca3089886a5e71dcfef85b0fa3b'
PACKAGE = 'com.saebm.btcnfcseller'
PERMISSIONS = {'android.permission.NFC', 'android.permission.CAMERA', 'android.permission.INTERNET',
               PACKAGE + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}


def validate_apk(apk, badging, signer, version, code):
    packages = re.findall(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if packages != [(PACKAGE, str(code), version)] or 'application-debuggable' in badging:
        raise ValueError('Wrong package/version or debuggable release')
    permissions = re.findall(r"^uses-permission[^:]*: name='([^']+)'", badging, re.M)
    if set(permissions) != PERMISSIONS:
        raise ValueError('Unexpected APK permissions')
    fingerprints = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', signer, re.M)
    if [value.lower() for value in fingerprints] != [EXPECTED_CERT]:
        raise ValueError('Stable signing certificate mismatch or multiple signers')
    try:
        with zipfile.ZipFile(apk) as archive:
            if archive.testzip() is not None or 'AndroidManifest.xml' not in archive.namelist():
                raise ValueError('Invalid APK ZIP contents')
    except zipfile.BadZipFile as exc:
        raise ValueError('Invalid APK ZIP') from exc


if __name__ == '__main__':
    from release_guard import read_version
    apk = Path(sys.argv[1])
    sdk_tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/34.0.0'
    signer = subprocess.check_output([str(sdk_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
    badging = subprocess.check_output([str(sdk_tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
    validate_apk(apk, badging, signer, *read_version())
    print(signer, end='')
    print('PASS: release package/version/signature/permissions/ZIP')
