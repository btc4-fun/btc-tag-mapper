package com.saebm.btcnfcseller;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import com.saebm.btcnfcseller.core.BitcoinAddress;
import com.saebm.btcnfcseller.core.Capture;
import com.saebm.btcnfcseller.core.Uid;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** App-private, append-only captures. */
public final class CaptureStore extends SQLiteOpenHelper {
    public enum Result { SAVED, DUPLICATE, UID_CONFLICT, ADDRESS_CONFLICT }
    private final Clock clock;

    public CaptureStore(Context context) { this(context, Clock.systemUTC()); }

    public CaptureStore(Context context, Clock clock) {
        super(context.getApplicationContext(), "captures.db", null, 1);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE captures (uid TEXT NOT NULL PRIMARY KEY, address TEXT NOT NULL UNIQUE, "
                + "captured_at TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new SQLiteException("Unsupported capture schema upgrade");
    }

    /**
     * Saves a canonical mapping without replacement. UID conflict takes precedence
     * if both supplied identities already belong to different captures.
     * Invalid input throws IllegalArgumentException; storage errors propagate so
     * the UI must not report success. Replays do not consult or change the clock.
     */
    public Result save(String uid, String address) {
        if (!Uid.isValid(uid) || !BitcoinAddress.isValid(address)) {
            throw new IllegalArgumentException("Invalid UID or Bitcoin address");
        }
        SQLiteDatabase db = getWritableDatabase();
        // Acquire the SQLite writer transaction before checking either identity.
        db.beginTransaction();
        try {
            try (Cursor existing = db.query("captures", new String[]{"address"},
                    "uid = ?", new String[]{uid}, null, null, null)) {
                if (existing.moveToFirst()) {
                    return address.equals(existing.getString(0)) ? Result.DUPLICATE : Result.UID_CONFLICT;
                }
            }
            try (Cursor existing = db.query("captures", new String[]{"uid"},
                    "address = ?", new String[]{address}, null, null, null)) {
                if (existing.moveToFirst()) return Result.ADDRESS_CONFLICT;
            }
            ContentValues values = new ContentValues();
            values.put("uid", uid);
            values.put("address", address);
            values.put("captured_at", clock.instant().truncatedTo(ChronoUnit.SECONDS).toString());
            db.insertOrThrow("captures", null, values);
            db.setTransactionSuccessful();
            return Result.SAVED;
        } finally {
            db.endTransaction();
        }
    }

    public List<String> days() {
        List<String> days = new ArrayList<>();
        try (Cursor rows = getReadableDatabase().rawQuery(
                "SELECT DISTINCT substr(captured_at, 1, 10) AS day FROM captures ORDER BY day DESC", null)) {
            while (rows.moveToNext()) days.add(rows.getString(0));
        }
        return days;
    }

    public List<Capture> forDay(String day) {
        List<Capture> captures = new ArrayList<>();
        try (Cursor rows = getReadableDatabase().query("captures",
                new String[]{"uid", "address", "captured_at"}, "substr(captured_at, 1, 10) = ?",
                new String[]{day}, null, null, "captured_at ASC, uid ASC")) {
            while (rows.moveToNext()) {
                captures.add(new Capture(rows.getString(0), rows.getString(1), rows.getString(2)));
            }
        }
        return captures;
    }
}
