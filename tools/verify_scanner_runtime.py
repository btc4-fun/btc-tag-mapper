"""Check packaged runtime, not Robolectric's broader test classpath."""
import os
import pathlib
import subprocess
import sys

sdk = pathlib.Path(os.environ['ANDROID_HOME'])
analyzer = sdk / 'cmdline-tools/latest/bin/apkanalyzer'
result = subprocess.run([str(analyzer), 'dex', 'packages', '--defined-only', sys.argv[1]],
                        capture_output=True, text=True, check=True)
classes = {line.split('\t')[-1] for line in result.stdout.splitlines() if line.startswith('C d ')}
for name in ['androidx.core.content.ContextCompat', 'androidx.core.app.ActivityCompat',
             'com.saebm.btcnfcseller.SecureCaptureActivity']:
    if name not in classes:
        raise SystemExit('Missing packaged scanner runtime class: ' + name)
print('PASS: required scanner runtime classes are defined in APK')
