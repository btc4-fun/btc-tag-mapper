# Final local verification

Final candidate: package com.saebm.btcnfcseller, version 1.0/code1, minimum API26, target35.

- Gradle core tests + Android host tests + lintDebug + assembleDebug: BUILD SUCCESSFUL. Final run used cached --offline dependencies.
- JUnit XML total: 43 tests, zero failures/errors (16 core, 16 storage, 10 UI, 1 QR gate).
- Actual Java exporter -> actual read-only Python importer: PASS, 5001 records in 5000+1 parts, 5946 validator comparisons, idempotent replay, three conflict cases atomically rejected, original serials/metadata preserved; temporary database removed.
- APK ZIP integrity and apksigner verification passed. Debug signing, not production release signing.
- APK size: 362731 bytes.
- APK SHA256: 601e6e1117cd2404a25de6691fbf5b37cac5a022a3a297b98819434548658534
- Signer certificate SHA256: 348ff59439b64016ce70fdb166a84bb2343c4147f135f90d624885682517fd43
- Only NFC and CAMERA permissions in actual APK; INTERNET absent. Merged application allowBackup=false and extraction exclusions retained.
- APK: /home/saeb/PycharmProjects/btc_nfc_seller/app/build/outputs/apk/debug/app-debug.apk

Independent source review raised three findings; each was followed by an executed RED/GREEN regression:

1. Screenshot/task snapshot protection: added FLAG_SECURE to entry and custom internal scanner activity; host tests inspect both window flags. OEM task snapshot behavior remains untested.
2. Cancelled export snapshots: cleanup on cancellation/replacement/preparation/output failure, scoped to owned filename. Regression proves cancellation deletes the export snapshot without touching another cache file.
3. NFC toggles while resumed: foreground ACTION_ADAPTER_STATE_CHANGED receiver refreshes actual adapter state and reader mode; test verifies on/off without resume and no re-enable after pause. Initial missing NFC test feature was corrected before the genuine assertion RED run.

Non-blocking build notes: legacy Android/ZXing API deprecation notes, manifest removal directive with no upstream INTERNET declaration, and lint advisory for a newer Robolectric version. No lint errors.

Not verified: physical NFC/tag/camera, independent wireless ADB, emulator execution, actual Android force-stop/reboot/power-loss, or OEM file-provider/task-snapshot behavior. No device transports/mDNS endpoints or emulator/AVD images were available. Database reopen/rollback/concurrent-writer and rotation tests are host evidence only.

No reference-repository writes, production access, commit, push or public release performed. The owner importer validates but does not persist captured_at; retain exported CSVs as the capture audit record.
