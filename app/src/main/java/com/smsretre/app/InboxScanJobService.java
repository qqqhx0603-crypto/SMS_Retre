package com.smsretre.app;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.Telephony;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InboxScanJobService extends JobService {
    private static final String TAG = "InboxScanJobService";

    private ExecutorService executor;

    @Override
    public boolean onStartJob(final JobParameters params) {
        executor = Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    scanAndSendSummary(InboxScanJobService.this);
                } catch (Exception e) {
                    Log.w(TAG, "Inbox scan failed", e);
                } finally {
                    jobFinished(params, false);
                }
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (executor != null) {
            executor.shutdownNow();
        }
        return true;
    }

    static void schedulePeriodic(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        ComponentName componentName = new ComponentName(context, InboxScanJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(Constants.INBOX_SCAN_JOB_ID, componentName)
                .setPeriodic(Constants.INBOX_SCAN_INTERVAL_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();
        scheduler.schedule(jobInfo);
    }

    private static void scanAndSendSummary(Context context) throws Exception {
        ConfigStore configStore = new ConfigStore(context);
        MailConfig config = configStore.load();
        if (!config.enabled || !config.isComplete()) {
            return;
        }
        if (!hasReadSmsPermission(context)) {
            Log.w(TAG, "READ_SMS permission is not granted; skipping inbox scan");
            return;
        }

        long now = System.currentTimeMillis();
        long lastScanAt = configStore.getLastInboxScanAt();
        long since = lastScanAt > 0L
                ? Math.max(0L, lastScanAt - Constants.INBOX_SCAN_OVERLAP_MS)
                : Math.max(0L, now - Constants.INBOX_SCAN_INTERVAL_MS - Constants.INBOX_SCAN_OVERLAP_MS);

        SmsDatabase database = new SmsDatabase(context);
        List<ScannedSms> missed = readMissedInboxMessages(context, database, since);
        if (missed.isEmpty()) {
            configStore.setLastInboxScanAt(now);
            return;
        }

        new MailSender().send(
                config,
                "SMS-Retre: 漏转短信汇总 " + missed.size() + " 条 " + TimeUtils.compact(now),
                buildSummaryBody(missed, since, now)
        );
        for (ScannedSms sms : missed) {
            database.insertScannedSentSms(
                    sms.sender,
                    sms.receivedAt,
                    sms.body,
                    config.receiverLabelFor(-1, sms.subscriptionId),
                    sms.subscriptionId
            );
        }
        configStore.setLastInboxScanAt(now);
    }

    private static List<ScannedSms> readMissedInboxMessages(Context context, SmsDatabase database, long since) {
        ArrayList<ScannedSms> result = new ArrayList<>();
        String[] projection = new String[]{
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.SUBSCRIPTION_ID
        };
        String selection = Telephony.Sms.DATE + " >= ?";
        String[] selectionArgs = new String[]{String.valueOf(since)};

        try (Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                Telephony.Sms.DATE + " ASC"
        )) {
            if (cursor == null) {
                return result;
            }
            int addressColumn = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
            int bodyColumn = cursor.getColumnIndex(Telephony.Sms.BODY);
            int dateColumn = cursor.getColumnIndex(Telephony.Sms.DATE);
            int subIdColumn = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID);
            while (cursor.moveToNext()) {
                String sender = getString(cursor, addressColumn, "unknown");
                String body = getString(cursor, bodyColumn, "");
                long receivedAt = getLong(cursor, dateColumn, 0L);
                int subscriptionId = getInt(cursor, subIdColumn, -1);
                if (body.trim().isEmpty() || receivedAt <= 0L) {
                    continue;
                }
                if (database.hasSimilarUnfailedSms(sender, body, receivedAt, Constants.INBOX_SCAN_DUPLICATE_WINDOW_MS)) {
                    continue;
                }
                result.add(new ScannedSms(sender, body, receivedAt, subscriptionId));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to query SMS inbox", e);
        }
        return result;
    }

    private static String buildSummaryBody(List<ScannedSms> messages, long since, long now) {
        StringBuilder builder = new StringBuilder();
        builder.append("SMS-Retre 定时扫描发现未转发短信\n\n")
                .append("扫描范围：").append(TimeUtils.full(since)).append(" - ").append(TimeUtils.full(now)).append("\n")
                .append("发现数量：").append(messages.size()).append("\n")
                .append("发送时间：").append(TimeUtils.full(now)).append("\n\n");
        for (int i = 0; i < messages.size(); i++) {
            ScannedSms sms = messages.get(i);
            builder.append("---- #").append(i + 1).append(" ----\n")
                    .append("发件号码：").append(sms.sender).append("\n")
                    .append("接收时间：").append(TimeUtils.full(sms.receivedAt)).append("\n")
                    .append("Subscription ID：").append(sms.subscriptionId < 0 ? "unknown" : String.valueOf(sms.subscriptionId)).append("\n")
                    .append("短信内容：\n")
                    .append(sms.body)
                    .append("\n\n");
        }
        return builder.toString();
    }

    private static boolean hasReadSmsPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private static String getString(Cursor cursor, int column, String fallback) {
        if (column < 0 || cursor.isNull(column)) {
            return fallback;
        }
        String value = cursor.getString(column);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static long getLong(Cursor cursor, int column, long fallback) {
        if (column < 0 || cursor.isNull(column)) {
            return fallback;
        }
        return cursor.getLong(column);
    }

    private static int getInt(Cursor cursor, int column, int fallback) {
        if (column < 0 || cursor.isNull(column)) {
            return fallback;
        }
        return cursor.getInt(column);
    }

    private static final class ScannedSms {
        final String sender;
        final String body;
        final long receivedAt;
        final int subscriptionId;

        ScannedSms(String sender, String body, long receivedAt, int subscriptionId) {
            this.sender = sender;
            this.body = body;
            this.receivedAt = receivedAt;
            this.subscriptionId = subscriptionId;
        }
    }
}
