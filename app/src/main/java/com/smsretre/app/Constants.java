package com.smsretre.app;

final class Constants {
    static final String ACTION_PROCESS_QUEUE = "com.smsretre.app.PROCESS_QUEUE";
    static final String NOTIFICATION_CHANNEL_ID = "sms_forwarding";
    static final int NOTIFICATION_ID = 1962;
    static final int FORWARD_JOB_ID = 1962001;
    static final int BATTERY_JOB_ID = 1962002;
    static final int SMS_SWEEP_JOB_ID = 1962003;

    static final long RETRY_WINDOW_MS = 5 * 60 * 1000L;
    static final long RETRY_INTERVAL_MS = 60 * 1000L;
    static final long SMS_SWEEP_LOOKBACK_MS = 30 * 60 * 1000L;
    static final int MAX_ATTEMPTS = 6;
    static final int BATTERY_ALERT_LEVEL = 10;

    static final String SMTP_HOST = "smtp.qq.com";
    static final int SMTP_SSL_PORT = 465;

    private Constants() {
    }
}
