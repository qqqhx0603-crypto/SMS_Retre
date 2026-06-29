package com.smsretre.app;

import android.content.Context;
import android.util.Log;

import java.util.List;

final class QueueProcessor {
    private static final String TAG = "QueueProcessor";

    private QueueProcessor() {
    }

    static void process(Context context) throws InterruptedException {
        SmsDatabase database = new SmsDatabase(context);
        ConfigStore configStore = new ConfigStore(context);
        MailSender sender = new MailSender();

        while (!Thread.currentThread().isInterrupted()) {
            long now = System.currentTimeMillis();
            List<SmsRecord> dueRecords = database.getDuePending(now, 10);
            if (dueRecords.isEmpty()) {
                long nextAt = database.getNextPendingAt();
                if (nextAt <= 0L) {
                    break;
                }
                long delay = Math.max(0L, Math.min(nextAt - now, Constants.RETRY_INTERVAL_MS));
                if (delay > 0L) {
                    Thread.sleep(delay);
                }
                continue;
            }

            MailConfig config = configStore.load();
            for (SmsRecord record : dueRecords) {
                attemptSend(database, sender, config, record);
            }
        }
    }

    private static void attemptSend(SmsDatabase database, MailSender sender, MailConfig config, SmsRecord record) {
        long now = System.currentTimeMillis();
        long firstAttemptAt = record.firstAttemptAt == 0L ? now : record.firstAttemptAt;
        int nextAttemptCount = record.attemptCount + 1;
        database.markAttemptStarted(record.id, firstAttemptAt, nextAttemptCount, now);

        try {
            sender.send(config, buildSubject(record), buildBody(record));
            database.markSent(record.id, System.currentTimeMillis());
        } catch (Exception e) {
            long failedAt = System.currentTimeMillis();
            boolean exhausted = nextAttemptCount >= Constants.MAX_ATTEMPTS
                    || failedAt - firstAttemptAt >= Constants.RETRY_WINDOW_MS;
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (exhausted) {
                database.markFinalFailed(record.id, error);
            } else {
                database.scheduleRetry(record.id, failedAt + Constants.RETRY_INTERVAL_MS, error);
            }
            Log.w(TAG, "SMS mail send failed, attempt=" + nextAttemptCount + ", exhausted=" + exhausted, e);
        }
    }

    private static String buildSubject(SmsRecord record) {
        if (SmsRecord.TYPE_BATTERY.equals(record.recordType)) {
            return "SMS-Retre: 备用机低电量 " + record.batteryLevel + "%";
        }
        return "SMS-Retre: " + record.sender + " " + TimeUtils.compact(record.receivedAt);
    }

    private static String buildBody(SmsRecord record) {
        if (SmsRecord.TYPE_BATTERY.equals(record.recordType)) {
            return "备用机低电量提醒\n\n"
                    + "当前电量：" + record.batteryLevel + "%\n"
                    + "检测时间：" + TimeUtils.full(record.receivedAt) + "\n"
                    + "记录编号：" + record.id + "\n"
                    + "提示：请尽快给备用机充电。\n";
        }
        return "备用机收到新短信\n\n"
                + "发件号码：" + record.sender + "\n"
                + "接收号码：" + cleanReceiverLabel(record.receiverLabel) + "\n"
                + "接收卡槽：" + formatSlot(record.receiverSlot) + "\n"
                + "Subscription ID：" + formatId(record.receiverSubId) + "\n"
                + "接收时间：" + TimeUtils.full(record.receivedAt) + "\n"
                + "记录编号：" + record.id + "\n"
                + "短信内容：\n"
                + record.body + "\n";
    }

    private static String cleanReceiverLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return "unknown";
        }
        return label.trim();
    }

    private static String formatSlot(int slotIndex) {
        if (slotIndex < 0) {
            return "unknown";
        }
        return "SIM" + (slotIndex + 1) + " (slot " + slotIndex + ")";
    }

    private static String formatId(int id) {
        return id < 0 ? "unknown" : String.valueOf(id);
    }
}
