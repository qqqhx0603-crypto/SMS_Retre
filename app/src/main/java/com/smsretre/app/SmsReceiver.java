package com.smsretre.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.ArrayList;

public final class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !isSmsAction(intent.getAction())) {
            return;
        }

        ConfigStore configStore = new ConfigStore(context);
        MailConfig config = configStore.load();
        if (!config.enabled) {
            Log.i(TAG, "SMS forwarding disabled; ignoring new SMS");
            return;
        }

        ArrayList<SmsMessage> messages = readMessages(intent);
        if (messages.isEmpty()) {
            Log.w(TAG, "SMS broadcast contains no readable messages: " + intent.getAction());
            return;
        }

        String sender = "";
        long receivedAt = System.currentTimeMillis();
        StringBuilder body = new StringBuilder();
        for (SmsMessage message : messages) {
            if (sender.isEmpty() && message.getOriginatingAddress() != null) {
                sender = message.getOriginatingAddress();
            }
            if (message.getTimestampMillis() > 0) {
                receivedAt = message.getTimestampMillis();
            }
            String part = message.getMessageBody();
            if (part == null || part.isEmpty()) {
                part = message.getDisplayMessageBody();
            }
            if (part != null) {
                body.append(part);
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

    private static boolean isSmsAction(String action) {
        return Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)
                || Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(action);
    }

    private static ArrayList<SmsMessage> readMessages(Intent intent) {
        ArrayList<SmsMessage> result = new ArrayList<>();
        SmsMessage[] frameworkMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (frameworkMessages != null) {
            for (SmsMessage message : frameworkMessages) {
                if (message != null) {
                    result.add(message);
                }
            }
        }
        if (!result.isEmpty()) {
            return result;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            return result;
        }
        Object[] pdus = (Object[]) extras.get("pdus");
        if (pdus == null || pdus.length == 0) {
            return result;
        }
        String format = extras.getString("format");
        for (Object pdu : pdus) {
            if (!(pdu instanceof byte[])) {
                continue;
            }
            try {
                SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu, format);
                if (message != null) {
                    result.add(message);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to parse SMS PDU", e);
            }
        }
        return result;
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
