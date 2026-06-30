package com.smsretre.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class SmsDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "sms_retre.db";
    private static final int DB_VERSION = 2;

    private static final String TABLE = "sms_queue";

    SmsDatabase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "sender TEXT NOT NULL,"
                + "body TEXT NOT NULL,"
                + "received_at INTEGER NOT NULL,"
                + "created_at INTEGER NOT NULL,"
                + "status TEXT NOT NULL,"
                + "attempt_count INTEGER NOT NULL DEFAULT 0,"
                + "first_attempt_at INTEGER NOT NULL DEFAULT 0,"
                + "next_attempt_at INTEGER NOT NULL DEFAULT 0,"
                + "last_attempt_at INTEGER NOT NULL DEFAULT 0,"
                + "sent_at INTEGER NOT NULL DEFAULT 0,"
                + "last_error TEXT,"
                + "record_type TEXT NOT NULL DEFAULT 'SMS',"
                + "receiver_label TEXT NOT NULL DEFAULT '',"
                + "receiver_slot INTEGER NOT NULL DEFAULT -1,"
                + "receiver_sub_id INTEGER NOT NULL DEFAULT -1,"
                + "battery_level INTEGER NOT NULL DEFAULT -1"
                + ")");
        db.execSQL("CREATE INDEX idx_sms_queue_due ON " + TABLE + "(status, next_attempt_at)");
        db.execSQL("CREATE INDEX idx_sms_queue_created ON " + TABLE + "(created_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN record_type TEXT NOT NULL DEFAULT 'SMS'");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN receiver_label TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN receiver_slot INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN receiver_sub_id INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN battery_level INTEGER NOT NULL DEFAULT -1");
        }
    }

    synchronized long insertPendingSms(String sender, long receivedAt, String body, String receiverLabel, int receiverSlot, int receiverSubId) {
        ContentValues values = basePendingValues(sender, receivedAt, body, SmsRecord.TYPE_SMS);
        values.put("receiver_label", emptyToUnknown(receiverLabel));
        values.put("receiver_slot", receiverSlot);
        values.put("receiver_sub_id", receiverSubId);
        return getWritableDatabase().insertOrThrow(TABLE, null, values);
    }

    synchronized long insertPendingBatteryAlert(int batteryLevel, long observedAt) {
        String body = "备用机电量只剩 " + batteryLevel + "%，请尽快充电。";
        ContentValues values = basePendingValues("SYSTEM", observedAt, body, SmsRecord.TYPE_BATTERY);
        values.put("battery_level", batteryLevel);
        return getWritableDatabase().insertOrThrow(TABLE, null, values);
    }

    private ContentValues basePendingValues(String sender, long receivedAt, String body, String recordType) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("sender", emptyToUnknown(sender));
        values.put("body", body);
        values.put("received_at", receivedAt);
        values.put("created_at", now);
        values.put("status", SmsRecord.STATUS_PENDING);
        values.put("next_attempt_at", now);
        values.put("record_type", recordType);
        return values;
    }

    synchronized List<SmsRecord> getDuePending(long now, int limit) {
        String sql = "SELECT * FROM " + TABLE
                + " WHERE status = ? AND next_attempt_at <= ?"
                + " ORDER BY created_at ASC LIMIT ?";
        return queryRecords(sql, new String[]{
                SmsRecord.STATUS_PENDING,
                String.valueOf(now),
                String.valueOf(limit)
        });
    }

    synchronized long getNextPendingAt() {
        String sql = "SELECT MIN(next_attempt_at) FROM " + TABLE + " WHERE status = ?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{SmsRecord.STATUS_PENDING})) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
            return 0L;
        }
    }

    synchronized List<SmsRecord> getRecent(int limit) {
        String sql = "SELECT * FROM " + TABLE + " ORDER BY created_at DESC LIMIT ?";
        return queryRecords(sql, new String[]{String.valueOf(limit)});
    }

    synchronized int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE status = ?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{status})) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        }
    }

    synchronized void markAttemptStarted(long id, long firstAttemptAt, int attemptCount, long now) {
        ContentValues values = new ContentValues();
        values.put("first_attempt_at", firstAttemptAt);
        values.put("attempt_count", attemptCount);
        values.put("last_attempt_at", now);
        getWritableDatabase().update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
    }

    synchronized void markSent(long id, long now) {
        ContentValues values = new ContentValues();
        values.put("status", SmsRecord.STATUS_SENT);
        values.put("sent_at", now);
        values.put("last_error", "");
        getWritableDatabase().update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
    }

    synchronized void scheduleRetry(long id, long nextAttemptAt, String error) {
        ContentValues values = new ContentValues();
        values.put("status", SmsRecord.STATUS_PENDING);
        values.put("next_attempt_at", nextAttemptAt);
        values.put("last_error", trimError(error));
        getWritableDatabase().update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
    }

    synchronized void markFinalFailed(long id, String error) {
        ContentValues values = new ContentValues();
        values.put("status", SmsRecord.STATUS_FAILED);
        values.put("next_attempt_at", 0L);
        values.put("last_error", trimError(error));
        getWritableDatabase().update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
    }

    synchronized int resetFailuresToPending(long now) {
        ContentValues values = new ContentValues();
        values.put("status", SmsRecord.STATUS_PENDING);
        values.put("attempt_count", 0);
        values.put("first_attempt_at", 0L);
        values.put("next_attempt_at", now);
        values.put("last_attempt_at", 0L);
        values.put("sent_at", 0L);
        values.put("last_error", "");
        return getWritableDatabase().update(TABLE, values, "status = ?", new String[]{SmsRecord.STATUS_FAILED});
    }

    private List<SmsRecord> queryRecords(String sql, String[] args) {
        ArrayList<SmsRecord> records = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                records.add(fromCursor(cursor));
            }
        }
        return records;
    }

    private SmsRecord fromCursor(Cursor cursor) {
        SmsRecord record = new SmsRecord();
        record.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        record.sender = cursor.getString(cursor.getColumnIndexOrThrow("sender"));
        record.body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        record.receivedAt = cursor.getLong(cursor.getColumnIndexOrThrow("received_at"));
        record.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        record.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
        record.attemptCount = cursor.getInt(cursor.getColumnIndexOrThrow("attempt_count"));
        record.firstAttemptAt = cursor.getLong(cursor.getColumnIndexOrThrow("first_attempt_at"));
        record.nextAttemptAt = cursor.getLong(cursor.getColumnIndexOrThrow("next_attempt_at"));
        record.lastAttemptAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_attempt_at"));
        record.sentAt = cursor.getLong(cursor.getColumnIndexOrThrow("sent_at"));
        record.lastError = cursor.getString(cursor.getColumnIndexOrThrow("last_error"));
        record.recordType = cursor.getString(cursor.getColumnIndexOrThrow("record_type"));
        record.receiverLabel = cursor.getString(cursor.getColumnIndexOrThrow("receiver_label"));
        record.receiverSlot = cursor.getInt(cursor.getColumnIndexOrThrow("receiver_slot"));
        record.receiverSubId = cursor.getInt(cursor.getColumnIndexOrThrow("receiver_sub_id"));
        record.batteryLevel = cursor.getInt(cursor.getColumnIndexOrThrow("battery_level"));
        return record;
    }

    private static String emptyToUnknown(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value;
    }

    private static String trimError(String error) {
        if (error == null) {
            return "";
        }
        String cleaned = error.replace('\n', ' ').replace('\r', ' ').trim();
        if (cleaned.length() <= 500) {
            return cleaned;
        }
        return cleaned.substring(0, 500);
    }
}
