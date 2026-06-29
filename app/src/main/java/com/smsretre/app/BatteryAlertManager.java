package com.smsretre.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

final class BatteryAlertManager {
    private static final String TAG = "BatteryAlertManager";

    private BatteryAlertManager() {
    }

    static void checkAndEnqueue(Context context) {
        Context appContext = context.getApplicationContext();
        ConfigStore configStore = new ConfigStore(appContext);
        MailConfig config = configStore.load();
        if (!config.enabled || !config.isComplete()) {
            return;
        }

        Integer level = readBatteryPercent(appContext);
        if (level == null) {
            return;
        }

        if (level > Constants.BATTERY_ALERT_LEVEL) {
            configStore.setBatteryAlertSent(false);
            return;
        }

        if (configStore.isBatteryAlertSent()) {
            return;
        }

        try {
            SmsDatabase database = new SmsDatabase(appContext);
            database.insertPendingBatteryAlert(level, System.currentTimeMillis());
            configStore.setBatteryAlertSent(true);
            SmsForwardService.start(appContext);
        } catch (Exception e) {
            Log.e(TAG, "Failed to enqueue battery alert", e);
        }
    }

    private static Integer readBatteryPercent(Context context) {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            return null;
        }
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) {
            return null;
        }
        return Math.round(level * 100f / scale);
    }
}
