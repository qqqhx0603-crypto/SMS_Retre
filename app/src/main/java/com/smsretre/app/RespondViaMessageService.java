package com.smsretre.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public final class RespondViaMessageService extends Service {
    private static final String TAG = "RespondViaMessageService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Respond-via-message request ignored by SMS-Retre");
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
