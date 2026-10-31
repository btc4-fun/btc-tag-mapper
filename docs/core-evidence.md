# Core verification evidence

## Delivered API

Package `com.saebm.btcnfcseller.core`:

- `Uid.fromBytes(byte[])`: 4/7/10 bytes, original order, two uppercase hex digits per byte; invalid/null input throws `IllegalArgumentException`.
- `Uid.isValid(String)`: strict uppercase, no trimming/normalization.
- `BitcoinAddress.isValid(String)`: lowercase canonical mainnet witness-v0 20-byte Bech32 checksum; excludes both reference public preview addresses. No key derivation or network calls.
- Immutable `Capture(uid,address,capturedAt)` validates all three values and retains exact timestamp text. `utcDate()` supplies the grouping date.
- `CsvExporter.export(List<Capture>, String date)` selects the UTC day, preserves input order and capture timestamps, and returns numbered `Part.filename` / `Part.bytes`. Empty selection returns no files. Duplicate UID/address within the selected day is rejected, never silently dropped.
- Filenames: `2026-10-31-part-001.csv`, `2026-10-31-part-002.csv`, etc. UTF-8 without BOM, LF terminators, exact `uid,address,captured_at` header. Limits include header/newlines: 5000 records and 1048576 bytes inclusive. The overload with lower limits exercises otherwise unreachable byte boundaries and cannot raise the importer limits.

All validated fields are ASCII and cannot contain CSV delimiters, quotes or line endings; no escaping is necessary. Capture timestamp parsing deliberately supports Python's offsets through ±23:59 instead of Java ZoneOffset's ±18:00 limit. Seconds and timezone are mandatory; fractional seconds have 1–6 digits.

## Executed verification

From `/home/saeb/PycharmProjects/btc_nfc_seller`:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto \
ANDROID_HOME=/home/saeb/android-sdk ./gradlew :core:test --console=plain
bash core/run-tests.sh
PYTHONDONTWRITEBYTECODE=1 python3 -B tools/verify_import.py
```

Final Gradle execution: `BUILD SUCCESSFUL`; all 16 JUnit tests passed. Direct isolated JUnit execution: `OK (16 tests)`.

### Vertical TDD

Each implementation behavior was added after a real assertion-failing run, then the full current suite was rerun green before adding the next behavior:

1. UID byte formatting (ComparisonFailure), then unsupported lengths (missing exception).
2. Canonical UID validation (assertTrue failure).
3. Canonical Bitcoin checksum (assertTrue failure), then public-preview exclusion (assertFalse failure).
4. Capture exact values (expected UID, got null), binding validation (missing exception), timestamp validation (missing exception).
5. CSV exact wire format (expected one part, got zero), UTC selection (extra row), invalid input (missing exception).
6. CSV 4999/5000/5001 row boundary (expected two parts, got one), inclusive byte threshold (expected two parts, got one), invalid limit seam (missing exception), duplicate input (missing exception).

`core/tdd-runs.txt` retains actual stdout from canonical UID onward, including RED and GREEN runs and the byte-accounting refactor. Earlier byte-format/length RED/GREEN output is in the tool transcript. The additional other-witness/Bech32m regression test was already green on first run and is explicitly coverage, not claimed as a new RED/GREEN implementation cycle.

During development concurrent shared Gradle configuration temporarily could not resolve the Android plugin. No shared Gradle files were edited; `core/run-tests.sh` ran the same JUnit tests directly using Java 17 and cached JUnit 4.13.2/Hamcrest. Final Gradle runs subsequently succeeded. `git status` was unavailable because this directory was not a Git repository; no commits were attempted.

### Real importer proof

`tools/verify_import.py` snapshots and compiles actual Java production sources plus `core/src/proof/java/ExportProof.java`, executes that exporter, and passes its untouched bytes to the real read-only reference Python importer. It creates only a disposable scratch SQLite database and removes the fixture on completion. It does not accept a real database URL and disables Python bytecode writes before reference imports.

Actual result retained in `core/import-proof.json`:

- **5946** Java/Python inventory-validator comparisons match, including **5001** distinct synthetic witness programs, malformed/mutated forms, public previews, P2WSH, v1 and Bech32m.
- **5001** Java-exported records imported in **5000 + 1** rows; file sizes **425024 + 109 bytes**.
- Initial import: `(5000,0)` and `(1,0)`; replay: `(0,5000)` and `(0,1)`.
- Three conflict cases reject without state changes: address already bound, UID already bound, mixed pending/conflicting batch.
- Printed serial, address, Jalali timestamp, created timestamp and all three synthetic private metadata columns remain unchanged; fresh SQLite connections read back the results.
- Capture timestamps remain exact in CSV. The existing importer validates but intentionally does **not** persist `captured_at`; retain CSV as audit evidence.
- Manifest records production/proof source hashes, every executed Java class hash and the three reference module hashes. Disposable fixture removal is verified.

Fixture provenance: the reference project's existing `.venv/bin/python` with `bech32` encoded deterministic 20-byte integer programs `1..5001`; the two small unit fixtures use repeated byte 1/2 programs. These are public test bytes, not generated keys, recovered wallets or funded addresses. No network wallet lookup occurred.

## Scope

Created production/tests/proof/resources/evidence under `core/`, `tools/verify_import.py`, and this document only. The reference repository was read-only; no reference files/dependencies/configuration were written. Android NFC/camera/device/store behavior belongs to other agents and is not claimed by this proof.
