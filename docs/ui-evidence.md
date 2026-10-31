# Parent UI/build evidence

Actual executed vertical test cycles (Gradle :app:testDebugUnitTest with selected test classes):

1. MainActivityTest.startsOfflineWithExplicitSafetyAndNfcStatus: RED missing Activity assertion; GREEN after offline Persian shell/NFC hardware status.
2. tagReadRequiresExplicitReviewAndSurvivesRotation: RED missing acceptTag; GREEN after byte UID read seam and state restoration.
3. reviewShowsFullSnapshotAndRequiresConfirmation: RED missing confirmation dialog; GREEN after explicit snapshot review/save and duplicate feedback. Robolectric main looper drained after dialog buttons (callbacks are queued).
4. PublicQrTest: RED missing public QR gate; GREEN with strict reference-compatible address validation. Invalid/secret-like synthetic contents never echoed by gate.
5. scannerRequestsCameraAndDeniedPermissionIsClear: RED absent scanner button; GREEN after runtime camera permission handling and in-process bundled ZXing integration, image saving disabled.
6. dailyHistoryOpensDetailsAndExportsViaDocumentPicker: RED absent history; GREEN after UTC daily list/detail/numbered export picker. Extended coverage exercised actual exporter bytes through a registered synthetic content resolver output stream; no claim of real OEM provider verification.
7. invalidManualInputIsNeverPersistedInSavedState: RED invalid draft retained; GREEN after disabling automatic EditText state save and whitelisting canonical public address in explicit saved state.
8. appliesSystemInsetsForAndroidEdgeToEdge: RED content padding insufficient; GREEN after inset listener. A preliminary compile error from a hidden constructor was corrected in the fixture before the valid RED run.

Dependency discovery: direct Google Maven returned misleading 404 for AndroidX test artifacts; official downloads via approved local invocation-only proxy succeeded. Robolectric 4.13 uses monitor1.7.1. No proxy settings were written to project configuration. An initial offline build failed resolving the AGP plugin marker after refresh; this is not an offline build success.

Lint initially rejected a literal view ID; real id resources replaced all such identifiers. Subsequent lint/build passed with only the newer-Robolectric-version advisory. Final full-suite results and APK hashes belong in final verification output, not this chronological evidence note.

No physical phone, wireless endpoint or emulator was found via adb devices/mDNS, SDK inventory and AVD inventory. Robolectric is a host simulation, not physical device or Android emulator evidence.
