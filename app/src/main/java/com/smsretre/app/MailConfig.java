package com.smsretre.app;

final class MailConfig {
    final boolean enabled;
    final String senderEmail;
    final String authCode;
    final String recipientEmail;

    MailConfig(boolean enabled, String senderEmail, String authCode, String recipientEmail) {
        this.enabled = enabled;
        this.senderEmail = clean(senderEmail);
        this.authCode = authCode == null ? "" : authCode;
        this.recipientEmail = clean(recipientEmail);
    }

    boolean isComplete() {
        return !senderEmail.isEmpty() && !authCode.isEmpty() && !recipientEmail.isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
