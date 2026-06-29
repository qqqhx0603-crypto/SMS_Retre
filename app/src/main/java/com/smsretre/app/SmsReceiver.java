package com.smsretre.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

public final class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        ConfigStore configStore = new ConfigStore(context);
        MailConfig config = configStore.load();
        if (!config.enabled) {
            Log.i(TAG, "SMS forwarding disabled; ignoring new SMS");
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            return;
        }

        String sender = "";
        long receivedAt = System.currentTimeMillis();
        StringBuilder body = new StringBuilder();
        for (SmsMessage message : messages) {
            if (message == null) {
                continue;
            }
            if (sender.isEmpty() && message.getOriginatingAddress() != null) {
                sender = message.getOriginatingAddress();
            }
            if (message.getTimestampMillis() > 0) {
                receivedAt = message.getTimestampMillis();
            }
            if (message.getMessageBody() != null) {
                body.append(message.getMessageBody());
            }
        }

        String content = body.toString();
        if (content.trim().isEmpty()) {
            return;
        }

        try {
            ReceiverInfo receiverInfo = readReceiverInfo(intent, config);
            SmsDatabase database = new SmsDatabase(context);
            database.insertPendingSms(
                    sender,
                    receivedAt,
                    content,
                    receiverInfo.label,
                    receiverInfo.slotIndex,
                    receiverInfo.subscriptionId
            );
            SmsForwardService.start(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to enqueue SMS", e);
        }
    }

    private static ReceiverInfo readReceiverInfo(Intent intent, MailConfig config) {
        int slotIndex = firstExistingIntExtra(intent, new String[]{
                "android.telephony.extra.SLOT_INDEX",
                "slot",
                "simSlot",
                "phone"
        });
        int subscriptionId = firstExistingIntExtra(intent, new String[]{
                "android.telephony.extra.SUBSCRIPTION_INDEX",
                "subscription",
                "subId",
                "subscriptionId"
        });
        return new ReceiverInfo(config.receiverLabelFor(slotIndex, subscriptionId), slotIndex, subscriptionId);
    }

    private static int firstExistingIntExtra(Intent intent, String[] keys) {
        for (String key : keys) {
            if (intent.hasExtra(key)) {
                return intent.getIntExtra(key, -1);
            }
        }
        return -1;
    }

    private static final class ReceiverInfo {
        final String label;
        final int slotIndex;
        final int subscriptionId;

        ReceiverInfo(String label, int slotIndex, int subscriptionId) {
            this.label = label;
            this.slotIndex = slotIndex;
            this.subscriptionId = subscriptionId;
        }
    }
}
