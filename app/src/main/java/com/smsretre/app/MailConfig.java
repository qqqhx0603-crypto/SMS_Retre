package com.smsretre.app;

final class MailConfig {
    final boolean enabled;
    final String senderEmail;
    final String authCode;
    final String recipientEmail;
    final String sim1Label;
    final String sim2Label;

    MailConfig(boolean enabled, String senderEmail, String authCode, String recipientEmail, String sim1Label, String sim2Label) {
        this.enabled = enabled;
        this.senderEmail = clean(senderEmail);
        this.authCode = authCode == null ? "" : authCode;
        this.recipientEmail = clean(recipientEmail);
        this.sim1Label = clean(sim1Label);
        this.sim2Label = clean(sim2Label);
    }

    boolean isComplete() {
        return !senderEmail.isEmpty() && !authCode.isEmpty() && !recipientEmail.isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    String receiverLabelFor(int slotIndex, int subscriptionId) {
        if (slotIndex == 0 && !sim1Label.isEmpty()) {
            return sim1Label;
        }
        if (slotIndex == 1 && !sim2Label.isEmpty()) {
            return sim2Label;
        }
        if (slotIndex >= 0) {
            return "SIM" + (slotIndex + 1);
        }
        if (subscriptionId >= 0) {
            return "subId " + subscriptionId;
        }
        return "unknown";
    }
}
