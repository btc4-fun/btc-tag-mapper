package com.saebm.btcnfcseller;

import android.content.Context;
import com.saebm.btcnfcseller.core.Capture;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class CaptureStoreTest {
    private static final String UID = "00010203";
    private static final String UID2 = "00010204";
    // Synthetic repeated-byte witness programs; no private keys generated.
    private static final String ADDRESS = "bc1qqyqszqgpqyqszqgpqyqszqgpqyqszqgpyfl4f3";
    private static final String ADDRESS2 = "bc1qqgpqyqszqgpqyqszqgpqyqszqgpqyqsz4desz8";
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("captures.db");
    }

    private CaptureStore at(String instant) {
        return new CaptureStore(context, Clock.fixed(Instant.parse(instant), ZoneOffset.ofHours(9)));
    }

    @Test public void savePersistsExactValuesAndUtcSecondsAcrossReopen() throws Exception {
        try (CaptureStore store = at("2026-10-31T23:59:59.987Z")) {
            assertEquals(CaptureStore.Result.SAVED, store.save(UID, ADDRESS));
        }
        try (CaptureStore reopened = new CaptureStore(context)) {
            assertEquals(Collections.singletonList("2026-10-31"), reopened.days());
            List<Capture> records = reopened.forDay("2026-10-31");
            assertEquals(1, records.size());
            Capture capture = records.get(0);
            assertEquals(UID, capture.uid);
            assertEquals(ADDRESS, capture.address);
            assertEquals("2026-10-31T23:59:59Z", capture.capturedAt);
            assertTrue(context.getDatabasePath("captures.db").isFile());
            assertTrue(context.getDatabasePath("captures.db").getCanonicalPath()
                    .startsWith(context.getDataDir().getCanonicalPath() + "/"));
        }
    }

    @Test public void newStoreHasNoDays() {
        try (CaptureStore store = new CaptureStore(context)) {
            assertEquals(Collections.emptyList(), store.days());
        }
    }

    @Test public void utcRolloverUsesSaveTimeAndDaysAreDescending() {
        java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(
                Instant.parse("2026-10-31T23:59:59.999Z"));
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.ofHours(9); }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        try (CaptureStore store = new CaptureStore(context, clock)) {
            store.save(UID, ADDRESS);
            now.set(Instant.parse("2026-11-01T00:00:00.123Z"));
            store.save(UID2, ADDRESS2);
            assertEquals(java.util.Arrays.asList("2026-11-01", "2026-10-31"), store.days());
            assertEquals("2026-10-31T23:59:59Z", store.forDay("2026-10-31").get(0).capturedAt);
            assertEquals("2026-11-01T00:00:00Z", store.forDay("2026-11-01").get(0).capturedAt);
        }
    }

    @Test public void dayRowsAreTimestampOrderedWithUidTieBreaker() {
        try (CaptureStore store = at("2026-10-31T20:00:00Z")) { store.save(UID2, ADDRESS2); }
        try (CaptureStore store = at("2026-10-31T20:00:00Z")) { store.save(UID, ADDRESS); }
        try (CaptureStore store = at("2026-10-31T01:00:00Z")) {
            store.save("00010205", "bc1qqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcr5ac3gx");
            List<Capture> rows = store.forDay("2026-10-31");
            assertEquals(3, rows.size());
            assertEquals("00010205", rows.get(0).uid);
            assertEquals(UID, rows.get(1).uid);
            assertEquals(UID2, rows.get(2).uid);
            assertTrue(store.forDay("2026-10-30").isEmpty());
            assertTrue(store.forDay("2026-10-31' OR 1=1 --").isEmpty());
        }
    }

    @Test public void racingIdenticalSavesCommitExactlyOnce() throws Exception {
        race(UID, ADDRESS, CaptureStore.Result.DUPLICATE);
    }

    @Test public void racingUidConflictPreservesWinner() throws Exception {
        race(UID, ADDRESS2, CaptureStore.Result.UID_CONFLICT);
    }

    @Test public void racingAddressConflictPreservesWinner() throws Exception {
        race(UID2, ADDRESS, CaptureStore.Result.ADDRESS_CONFLICT);
    }

    private void race(String secondUid, String secondAddress, CaptureStore.Result expected) throws Exception {
        java.util.concurrent.CountDownLatch insideFirst = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseFirst = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch secondStarted = new java.util.concurrent.CountDownLatch(1);
        Clock paused = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() {
                insideFirst.countDown();
                try {
                    if (!releaseFirst.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new AssertionError("First save release timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                return Instant.parse("2026-10-31T12:00:00Z");
            }
        };
        java.util.concurrent.ExecutorService threads = java.util.concurrent.Executors.newFixedThreadPool(2);
        try (CaptureStore first = new CaptureStore(context, paused);
             CaptureStore second = at("2026-11-01T00:00:00Z")) {
            first.getWritableDatabase();
            second.getWritableDatabase();
            java.util.concurrent.Future<CaptureStore.Result> a = threads.submit(() -> first.save(UID, ADDRESS));
            assertTrue(insideFirst.await(10, java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.Future<CaptureStore.Result> b = threads.submit(() -> {
                secondStarted.countDown();
                return second.save(secondUid, secondAddress);
            });
            assertTrue(secondStarted.await(10, java.util.concurrent.TimeUnit.SECONDS));
            releaseFirst.countDown();
            assertEquals(CaptureStore.Result.SAVED, a.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(expected, b.get(10, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        try (CaptureStore reopened = new CaptureStore(context)) {
            assertEquals(Collections.singletonList("2026-10-31"), reopened.days());
            assertEquals(1, reopened.forDay("2026-10-31").size());
            Capture capture = reopened.forDay("2026-10-31").get(0);
            assertEquals(UID, capture.uid);
            assertEquals(ADDRESS, capture.address);
            assertEquals("2026-10-31T12:00:00Z", capture.capturedAt);
        }
    }

    @Test public void clockFailureReleasesTransactionWithoutCapture() {
        Clock broken = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { throw new IllegalStateException("injected clock failure"); }
        };
        try (CaptureStore store = new CaptureStore(context, broken)) {
            assertThrows(IllegalStateException.class, () -> store.save(UID, ADDRESS));
            assertFalse(store.getWritableDatabase().inTransaction());
            assertTrue(store.days().isEmpty());
        }
        try (CaptureStore reopened = at("2026-10-31T12:00:00Z")) {
            assertEquals(CaptureStore.Result.SAVED, reopened.save(UID, ADDRESS));
        }
    }

    @Test public void unsupportedUpgradeCallbackNeverDropsRecords() {
        try (CaptureStore store = at("2026-10-31T12:00:00Z")) {
            store.save(UID, ADDRESS);
            assertThrows(android.database.sqlite.SQLiteException.class,
                    () -> store.onUpgrade(store.getWritableDatabase(), 1, 2));
            assertEquals(1, store.forDay("2026-10-31").size());
            assertEquals(ADDRESS, store.forDay("2026-10-31").get(0).address);
        }
    }

    @Test public void unknownSchemaVersionFailsWithoutDestroyingRecords() {
        try (CaptureStore store = at("2026-10-31T12:00:00Z")) {
            store.save(UID, ADDRESS);
            store.getWritableDatabase().setVersion(2);
        }
        try (CaptureStore store = new CaptureStore(context)) {
            assertThrows(android.database.sqlite.SQLiteException.class, store::getWritableDatabase);
        }
        try (android.database.sqlite.SQLiteDatabase db = android.database.sqlite.SQLiteDatabase.openDatabase(
                context.getDatabasePath("captures.db").getPath(), null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
             android.database.Cursor row = db.rawQuery("SELECT uid, address FROM captures", null)) {
            assertEquals(2, db.getVersion());
            assertTrue(row.moveToFirst());
            assertEquals(UID, row.getString(0));
            assertEquals(ADDRESS, row.getString(1));
            assertFalse(row.moveToNext());
        }
    }

    @Test public void databaseItselfEnforcesUniqueUidAndAddress() {
        try (CaptureStore store = at("2026-10-31T12:00:00Z")) {
            store.save(UID, ADDRESS);
            android.database.sqlite.SQLiteDatabase db = store.getWritableDatabase();
            assertThrows(android.database.sqlite.SQLiteConstraintException.class,
                    () -> db.execSQL("INSERT INTO captures VALUES (?, ?, ?)",
                            new Object[]{UID, ADDRESS2, "2026-10-31T13:00:00Z"}));
            assertThrows(android.database.sqlite.SQLiteConstraintException.class,
                    () -> db.execSQL("INSERT INTO captures VALUES (?, ?, ?)",
                            new Object[]{UID2, ADDRESS, "2026-10-31T13:00:00Z"}));
            assertEquals(1, store.forDay("2026-10-31").size());
        }
    }

    @Test public void failedInsertRollsBackPartialStatementAndAllowsRetry() {
        try (CaptureStore store = at("2026-10-31T12:00:00Z")) {
            android.database.sqlite.SQLiteDatabase db = store.getWritableDatabase();
            db.execSQL("CREATE TRIGGER fail_capture AFTER INSERT ON captures BEGIN SELECT RAISE(FAIL, 'injected failure'); END");
            assertThrows(android.database.sqlite.SQLiteException.class, () -> store.save(UID, ADDRESS));
            assertTrue("Failed save must not leave its inserted row", store.days().isEmpty());
            assertFalse(db.inTransaction());
            db.execSQL("DROP TRIGGER fail_capture");
            assertEquals(CaptureStore.Result.SAVED, store.save(UID, ADDRESS));
        }
        try (CaptureStore reopened = new CaptureStore(context)) {
            assertEquals(1, reopened.forDay("2026-10-31").size());
        }
    }

    @Test public void invalidInputsAreRejectedWithoutNormalizationOrWrites() {
        String[][] invalid = {
                {null, ADDRESS}, {"0001020a", ADDRESS}, {" 00010203", ADDRESS},
                {"00010203\n", ADDRESS}, {"000102", ADDRESS}, {"00:01:02:03", ADDRESS},
                {UID, null}, {UID, ""}, {UID, " " + ADDRESS}, {UID, ADDRESS.toUpperCase(java.util.Locale.ROOT)},
                {UID, ADDRESS.substring(0, ADDRESS.length() - 1) + "q"},
                {UID, "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"},
                {UID, "bc1qzmtrqsfuaf6l6kkcsseumq26ukaphfj9skkug6"}
        };
        try (CaptureStore store = at("2026-10-31T12:00:00Z")) {
            for (String[] pair : invalid) {
                assertThrows(IllegalArgumentException.class, () -> store.save(pair[0], pair[1]));
                assertTrue(store.days().isEmpty());
            }
            assertEquals(CaptureStore.Result.SAVED, store.save(UID, ADDRESS));
        }
    }

    @Test public void addressConflictNeverOverwritesOriginal() {
        try (CaptureStore store = at("2026-10-31T12:00:00Z")) {
            store.save(UID, ADDRESS);
            assertEquals(CaptureStore.Result.ADDRESS_CONFLICT, store.save(UID2, ADDRESS));
            assertEquals(1, store.forDay("2026-10-31").size());
            assertEquals(UID, store.forDay("2026-10-31").get(0).uid);
        }
    }

    @Test public void uidConflictNeverOverwritesOriginal() {
        try (CaptureStore store = at("2026-10-31T12:00:00Z")) {
            store.save(UID, ADDRESS);
            assertEquals(CaptureStore.Result.UID_CONFLICT, store.save(UID, ADDRESS2));
            assertEquals(1, store.forDay("2026-10-31").size());
            assertEquals(ADDRESS, store.forDay("2026-10-31").get(0).address);
        }
    }

    @Test public void replayKeepsOriginalTimestampAndSingleRecord() {
        try (CaptureStore store = at("2026-10-31T23:59:59Z")) {
            assertEquals(CaptureStore.Result.SAVED, store.save(UID, ADDRESS));
        }
        try (CaptureStore store = at("2026-11-01T00:00:00Z")) {
            assertEquals(CaptureStore.Result.DUPLICATE, store.save(UID, ADDRESS));
            assertEquals(Collections.singletonList("2026-10-31"), store.days());
            assertEquals(1, store.forDay("2026-10-31").size());
            assertEquals("2026-10-31T23:59:59Z", store.forDay("2026-10-31").get(0).capturedAt);
            assertTrue(store.forDay("2026-11-01").isEmpty());
        }
    }
}
