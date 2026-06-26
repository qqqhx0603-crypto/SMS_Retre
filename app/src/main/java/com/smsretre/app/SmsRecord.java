package com.smsretre.app;

final class SmsRecord {
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_SENT = "SENT";
    static final String STATUS_FAILED = "FAILED";

    long id;
    String sender;
    String body;
    long receivedAt;
    long createdAt;
    String status;
    int attemptCount;
    long firstAttemptAt;
    long nextAttemptAt;
    long lastAttemptAt;
    long sentAt;
    String lastError;
}
