# Scanner regression history and current release boundary

## Current candidate: 1.0.0 / versionCode 3

The explicit production dependency `androidx.core:core:1.13.1` and the actual-APK class verifier below are retained. In this release preparation, the full serial core/app unit/lint/debug build passed again (43 JUnit tests), and `tools/verify_scanner_runtime.py` passed on the newly built debug APK. The release workflow runs it for both build types and the downloaded published APK. No emulator or physical-camera run was repeated in this preparation.

The display version is normalized from the earlier uncommitted `1.0.1` candidate to the requested first official `1.0.0`; Android versionCode increases from 2 to 3. Branding and package ID are unchanged. The newly authorized release certificate is:

```text
7c4ccefc998b2407b22c2a7a3bc97e9b5f9baca3089886a5e71dcfef85b0fa3b
```

The historical key below is **not** the current release identity. Do not infer an in-place upgrade from the earlier emulator install: a differently signed installed package cannot be replaced without a separate, owner-approved data-preservation/migration decision. This agent did not read signing secrets or build with the new release key.

## Preserved historical report — 1.0.1 / versionCode 2

The following is the prior worker's recorded evidence, not a new run of the current candidate.

Reproduced the shipped signed 1.0 APK from GitHub run 41225239689 on the available Android 15/API35 emulator, emulator-5556. Installed that exact APK, opened MainActivity, clicked the scan button, accepted the real camera permission dialog. The process crashed during CaptureActivity.onResume:

    NoClassDefFoundError: androidx/core/content/ContextCompat
    CaptureManager.openCameraWithPermission(CaptureManager.java:241)

The actual APK omitted AndroidX Core. Robolectric's broader test runtime had masked the missing production dependency; the window-security test only called create(), not resumed camera access.

Added explicit runtime dependency androidx.core:core:1.13.1. A new actual-APK gate tools/verify_scanner_runtime.py fails on the shipped APK, requiring defined ContextCompat and ActivityCompat classes (not merely referenced DEX strings), and passes on the new debug and signed release APKs. Both CI artifact paths now run this gate. AndroidX adds the app-scoped signature receiver permission; the exact CI permission allowlist includes it, not INTERNET or storage access.

Verified local core tests, Android tests, lint and debug build pass. Built release using the existing authorized owner key without printing credentials. VersionCode increases from 1 to 2, versionName from 1.0 to 1.0.1. Installed with adb install -r over the old signed release, no uninstall/data clear. Triggered the real scan button again: SecureCaptureActivity is top-resumed; camera logs show Opening camera, Configuring camera and Starting preview, with no fatal exception for the new app PID.

Release certificate SHA256:

cdee481eac2d81904d0bed6281b956fe56e4f67ca37c5479592f4c89a18912f4

Local release APK SHA256:

fd2dfb8ae58446599ae6177aba86e2beefa63dd21174be73c1244d7684cd5d5e

APK:

/home/saeb/PycharmProjects/btc_nfc_seller/app/build/outputs/apk/release/app-release.apk

Boundaries: this proves the reproduced launch crash and camera preview recovery on Android15 emulator, not physical QR optical decoding or Nothing Phone2/Android17 behavior. NFC and requested visual branding changes remain separate unresolved tasks. This correction is local and not yet committed/pushed or verified by a new hosted run. Do not confuse earlier successful CI with this candidate.
