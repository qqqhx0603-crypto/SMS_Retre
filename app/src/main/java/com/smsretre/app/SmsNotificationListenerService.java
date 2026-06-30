package com.smsretre.app;

import android.app.Notification;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public final class SmsNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "SmsNotificationFallback";
    private static final String[] KNOWN_SMS_PACKAGES = new String[]{
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.bbk.mms",
            "com.bbk.msg",
            "com.vivo.message",
            "com.vivo.mms"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !isSmsPackage(sbn.getPackageName())) {
            return;
        }

        MailConfig config = new ConfigStore(this).load();
        if (!config.enabled || !config.isComplete()) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) {
            return;
        }

        Bundle extras = notification.extras;
        final String title = clean(extras.getCharSequence(Notification.EXTRA_TITLE));
        final String body = readNotificationBody(extras);
        if (body.isEmpty()) {
            return;
        }

        final long receivedAt = sbn.getPostTime() > 0L ? sbn.getPostTime() : System.currentTimeMillis();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                enqueueIfBroadcastMissed(title, body, receivedAt);
            }
        }, Constants.NOTIFICATION_FALLBACK_DELAY_MS);
    }

    private void enqueueIfBroadcastMissed(String title, String body, long receivedAt) {
        try {
            SmsDatabase database = new SmsDatabase(this);
            long since = System.currentTimeMillis() - Constants.NOTIFICATION_DEDUPE_WINDOW_MS;
            if (database.hasRecentSmsBody(body, since)) {
                return;
            }
            String sender = title.isEmpty() ? "SMS notification" : "通知:" + title;
            database.insertPendingSms(sender, receivedAt, body, "notification fallback", -1, -1);
            SmsForwardService.start(this);
        } catch (Exception e) {
            Log.w(TAG, "Failed to enqueue SMS notification fallback", e);
        }
    }

    private boolean isSmsPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        String defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this);
        if (packageName.equals(defaultSmsPackage)) {
            return true;
        }
        for (String knownPackage : KNOWN_SMS_PACKAGES) {
            if (packageName.equals(knownPackage)) {
                return true;
            }
        }
        return false;
    }

    private static String readNotificationBody(Bundle extras) {
        String bigText = clean(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (!bigText.isEmpty()) {
            return bigText;
        }

        Object lines = extras.get(Notification.EXTRA_TEXT_LINES);
        if (lines instanceof CharSequence[]) {
            StringBuilder builder = new StringBuilder();
            CharSequence[] textLines = (CharSequence[]) lines;
            for (CharSequence line : textLines) {
                String value = clean(line);
                if (value.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(value);
            }
            if (builder.length() > 0) {
                return builder.toString();
            }
        }

        return clean(extras.getCharSequence(Notification.EXTRA_TEXT));
    }

    private static String clean(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
