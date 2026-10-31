# Offline Bitcoin NFC Seller

Independent Android seller application. The envelope-maker repository is a read-only contract reference, never an application dependency. No server integration, authentication, balance lookup, production database access, tag writes, tag locking, record deletion or rebinding exists.

## Build

Verified local toolchain:

- Java: `/usr/lib/jvm/java-17-amazon-corretto`
- Android SDK: `/home/saeb/android-sdk`; platform 35 and build-tools 34.0.0
- Gradle wrapper 8.9 (official wrapper JAR SHA-256 verified, distribution checksum pinned)
- Android Gradle Plugin 8.7.0
- Android 8.0/API 26 minimum; target API 35
- Package `com.saebm.btcnfcseller`, version 1.0 / code 1
- ZXing Embedded 4.3.0 and bundled ZXing core 3.4.1, no downloaded scanning model
- JUnit 4.13.2 and Robolectric 4.13 (host JVM Android simulation, NOT an emulator/device)

Run from this directory:

    export JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto
    export ANDROID_HOME=/home/saeb/android-sdk
    ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

`local.properties` is machine-local and ignored. When Google Maven responds with misleading 404s, use an approved proxy through invocation-specific Gradle JVM flags; do not add host proxy settings to source. A network-free app does not imply the initial build can resolve dependencies without internet.

## Use

The interface is Persian and RTL. Identifiers, public addresses and timestamps are isolated LTR values. Bring a supported tag near the foreground application; type/paste a canonical public address or scan its QR. Review the entire UID and address and explicitly confirm. A tag read alone does not save anything. Repeat mappings and conflicting mappings do not overwrite records. A UID is cloneable identification, not authenticity proof; it is distinct from the printed wallet serial.

Daily history is grouped by UTC Gregorian date. Open a record to see full details, or select export for the day. Each numbered CSV part is saved separately via Android's document picker. Save ALL listed parts; successful export is not server import. App-private original records are retained. Uninstalling or clearing app data destroys those originals, so retain exports independently. Automatic cloud backup and device-transfer backup are excluded.

Only NFC and camera permissions are intended. No broad storage permission or INTERNET permission. Camera frames are decoded in memory; barcode-image saving is disabled. Only a raw canonical address QR is accepted; `bitcoin:` payment URIs, websites, mixed/uppercase addresses, preview addresses, private keys and recovery text are rejected. Invalid scanned content is not displayed or persisted. Manual invalid draft content is not saved in Android instance state.

## Wire contract

UTF-8, no BOM; exact header/order:

    uid,address,captured_at

UID is uppercase contiguous hex, 4/7/10 bytes in `Tag.getId()` order, retaining leading zeroes. Address is lowercase mainnet v0 P2WPKH, twenty-byte witness program, verified Bech32 checksum, excluding the two reference preview addresses. Capture time is generated when committing locally, UTC with seconds, and remains unchanged on export. Every file is at most 5000 rows and 1048576 bytes including the header. Filenames identify the UTC date and part number. Import requires pre-existing inventory addresses; it neither creates wallets nor replaces printed serials. The current owner importer validates but does not persist `captured_at`; retain CSVs as the capture audit trail.

## Verification boundaries

Automated core, SQLite-backed Robolectric and UI tests are local evidence. They are not physical NFC/camera evidence. No ADB transports, mDNS debugging endpoints or installed emulator/system image were found during this run. Wireless ADB, physical tag reads, real camera decoding, OEM file-provider behavior and actual device force-stop/reboot remain unverified. The debug APK is for local evaluation, not a production signing/release claim. No commit, push or public release was authorized or performed.
