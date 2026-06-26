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
        if (!configStore.load().enabled) {
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
            SmsDatabase database = new SmsDatabase(context);
            database.insertPending(sender, receivedAt, content);
            SmsForwardService.start(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to enqueue SMS", e);
        }
    }
}
