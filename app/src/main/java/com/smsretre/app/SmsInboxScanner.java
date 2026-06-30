package com.smsretre.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Log;

final class SmsInboxScanner {
    private static final String TAG = "SmsInboxScanner";
    private static final Uri INBOX_URI = Telephony.Sms.Inbox.CONTENT_URI;

    private SmsInboxScanner() {
    }

    static int scanRecent(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return 0;
        }

        ConfigStore configStore = new ConfigStore(appContext);
        MailConfig config = configStore.load();
        if (!config.enabled) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long lastScanAt = configStore.getLastInboxScanAt();
        long scanFrom = lastScanAt <= 0L ? now - Constants.SMS_SWEEP_LOOKBACK_MS : lastScanAt - 5000L;
        scanFrom = Math.max(scanFrom, now - Constants.SMS_SWEEP_LOOKBACK_MS);

        SmsDatabase database = new SmsDatabase(appContext);
        int inserted = 0;
        long maxSeenDate = lastScanAt;

        try (Cursor cursor = appContext.getContentResolver().query(
                INBOX_URI,
                null,
                "date >= ?",
                new String[]{String.valueOf(scanFrom)},
                "date ASC"
        )) {
            if (cursor == null) {
                return 0;
            }
            while (cursor.moveToNext()) {
                String sender = readString(cursor, Telephony.Sms.ADDRESS);
                String body = readString(cursor, Telephony.Sms.BODY);
                long receivedAt = readLong(cursor, Telephony.Sms.DATE, 0L);
                if (receivedAt <= 0L || body.trim().isEmpty()) {
                    continue;
                }
                maxSeenDate = Math.max(maxSeenDate, receivedAt);
                if (database.existsSimilarSms(sender, receivedAt, body)) {
                    continue;
                }

                int subId = readFirstExistingInt(cursor, new String[]{"sub_id", "subscription_id"}, -1);
                int slot = readFirstExistingInt(cursor, new String[]{"sim_slot", "slot", "phone_id", "sim_id"}, -1);
                String receiverLabel = config.receiverLabelFor(slot, subId);
                database.insertPendingSms(sender, receivedAt, body, receiverLabel, slot, subId);
                inserted++;
            }
        } catch (Exception e) {
            Log.w(TAG, "Inbox scan failed", e);
        } finally {
            configStore.setLastInboxScanAt(Math.max(now, maxSeenDate));
        }

        if (inserted > 0) {
            SmsForwardService.start(appContext);
        }
        return inserted;
    }

    private static String readString(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        if (index < 0 || cursor.isNull(index)) {
            return "";
        }
        return cursor.getString(index);
    }

    private static long readLong(Cursor cursor, String columnName, long fallback) {
        int index = cursor.getColumnIndex(columnName);
        if (index < 0 || cursor.isNull(index)) {
            return fallback;
        }
        return cursor.getLong(index);
    }

    private static int readFirstExistingInt(Cursor cursor, String[] columnNames, int fallback) {
        for (String columnName : columnNames) {
            int index = cursor.getColumnIndex(columnName);
            if (index >= 0 && !cursor.isNull(index)) {
                return cursor.getInt(index);
            }
        }
        return fallback;
    }
}
