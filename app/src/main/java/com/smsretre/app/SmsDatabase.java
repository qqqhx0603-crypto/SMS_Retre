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
    private static final int DB_VERSION = 1;

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
                + "last_error TEXT"
                + ")");
        db.execSQL("CREATE INDEX idx_sms_queue_due ON " + TABLE + "(status, next_attempt_at)");
        db.execSQL("CREATE INDEX idx_sms_queue_created ON " + TABLE + "(created_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("No database migration for " + oldVersion + " -> " + newVersion);
    }

    synchronized long insertPending(String sender, long receivedAt, String body) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("sender", emptyToUnknown(sender));
        values.put("body", body);
        values.put("received_at", receivedAt);
        values.put("created_at", now);
        values.put("status", SmsRecord.STATUS_PENDING);
        values.put("next_attempt_at", now);
        return getWritableDatabase().insertOrThrow(TABLE, null, values);
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
