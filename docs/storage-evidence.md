# CaptureStore verification evidence

## Scope and contract

Storage owns only:

- `app/src/main/java/com/saebm/btcnfcseller/CaptureStore.java`
- `app/src/test/java/com/saebm/btcnfcseller/CaptureStoreTest.java`
- This document.

`CaptureStore` is a `SQLiteOpenHelper`, storing `captures.db` in app-private
Android database storage. `save(uid,address)` validates without normalization,
opens an exclusive SQLite writer transaction before checking either identity,
and commits a new mapping with a save-time UTC whole-second timestamp.
The schema independently enforces unique UID and unique address. Exact replay
returns DUPLICATE without reading the clock or modifying data; conflicting
mapping returns UID_CONFLICT or ADDRESS_CONFLICT. UID takes precedence if both
identities conflict. No edit/delete API or destructive migration exists.
Storage/clock exceptions propagate; callers must not report successful save.

`days()` returns descending UTC ISO dates. `forDay(day)` returns captures ordered
by timestamp ascending, then UID ascending to break ties. Its parameter is bound,
not interpolated. Unknown dates return an empty list.

## Test commands

```sh
JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto \
ANDROID_HOME=/home/saeb/android-sdk \
./gradlew :app:testDebugUnitTest --tests '*CaptureStoreTest' --console=plain --offline

JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto \
ANDROID_HOME=/home/saeb/android-sdk \
./gradlew :app:testDebugUnitTest :core:test --console=plain --offline
```

Robolectric 4.13, SDK 28, JUnit 4.13.2. The parent owns dependency/build settings.

## Initial infrastructure blockers (not behavioral RED)

Before any storage production code existed, dependency resolution failed for
`androidx.test:monitor:1.7.2` with Robolectric 4.14.1. The parent changed to 4.13;
Google downloads for `monitor-1.7.1.aar` also failed directly. Offline lacked the
artifact, and a refresh also failed AGP resolution. The parent identified proxy
routing; resolving with the following JVM flags unblocked actual execution:

```text
-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=2080
-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=2080
```

Subsequent runs were offline. One concurrent-build run reported no matching
tests; rerunning with `--rerun-tasks` rebuilt the outputs and passed. None of
these infrastructure failures counts as behavioral RED evidence.

## Actual vertical RED/GREEN sequence

Each row was added and executed before implementing its behavior; the entire
storage suite was rerun after the corresponding implementation.

| Slice | Observed RED | Subsequent GREEN |
|---|---|---|
| Empty store API | 1 test / 1 failure: explicit assertion caused by missing CaptureStore class | 1 passed |
| File-backed save/reopen, exact fields, UTC second timestamp | 2 tests / 1 failure: missing injectable-clock constructor | 2 passed |
| Identical replay | 3 tests / 1 failure: SAVED instead of DUPLICATE | 3 passed |
| UID conflict | 4 tests / 1 failure: SAVED instead of UID_CONFLICT | 4 passed |
| Address conflict | 5 tests / 1 failure: SAVED instead of ADDRESS_CONFLICT | 5 passed |
| Input validation | 6 tests / 1 failure: expected IllegalArgumentException not produced | 6 passed |
| Transaction rollback | 7 tests / 1 failure: inserted row survived an AFTER INSERT trigger raising FAIL | 7 passed after explicit transaction/finally rollback |
| Database unique constraints | 8 tests / 1 failure: direct duplicate insert did not throw SQLiteConstraintException | 8 passed after unique UID/address schema |

Initial absent-API tests used reflection to obtain a meaningful assertion rather
than a Java compile error while other agents built core/UI. After GREEN they
were refactored to ordinary typed API calls.

## Additional existing-behavior coverage

After those eight implementation slices, eight more tests passed immediately,
without new production behavior; these are coverage, not invented RED claims:

- Save-time mutable clock crosses UTC midnight despite a +09:00 clock zone;
  days sort descending and nanoseconds are discarded.
- Out-of-insertion-order timestamps and same-second UID tie-breaks; absent date
  and SQL-shaped date return no unrelated records.
- Identical replay across two helpers/two threads leaves exactly one record.
- Competing UID mapping across two helpers/two threads preserves the winner.
- Competing address mapping across two helpers/two threads preserves the winner.
- Clock exception rolls back/releases transaction; later valid save succeeds.
- Unsupported upgrade callback throws without dropping the existing record.
- A future schema version refuses downgrade; independent read-only connection
  verifies its version and original record remain intact.

Races pause the first writer at its injected clock, start the second writer,
release the first, await bounded futures, then read the result through a freshly
opened helper. This is in-process concurrent SQLite evidence, not an OS lock
inspection or multi-process crash proof. Trigger RAISE(FAIL) specifically exposes
partial-statement writes: without an enclosing transaction the row survives,
which the observed RED demonstrated.

## Verification result and limits

The earlier full `:app:testDebugUnitTest :core:test` command returned BUILD
SUCCESSFUL. Parsed JUnit XML reported 16 CaptureStore tests, 0 failures, 0 errors.
That full app run also passed MainActivityTest (6) and PublicQrTest (1); core tasks
were already up to date with passing reports. A later rerun after the parent's
concurrent addition of `appliesSystemInsetsForAndroidEdgeToEdge` reported 24 app
tests / 1 failure in that new MainActivity test; all 16 storage tests remained
green. This unrelated UI RED is left to its owner, not hidden or repaired here.
Shared manifest merger warnings
and MainActivity deprecation notes appeared in some rebuilds; storage itself
introduced no compiler warning.

All fixtures use disposable app-private file-backed Robolectric databases,
explicitly closed helpers/connections, synthetic tag identifiers and witness
programs. Three addresses used in tests encode twenty repeated bytes (01, 02,
03), not generated private keys. Those and a fourth (04) were independently
accepted by executing the read-only reference inventory validator. No real
inventory, secret, server, funded wallet, commit, or reference write occurred.

Reopen persistence and transaction rollback are verified; actual Android
force-stop/power loss and physical-device storage behavior are not exercised by
this host-side suite. SQLite provides crash recovery, but this document does
not claim an executed process-kill or power-loss test.
