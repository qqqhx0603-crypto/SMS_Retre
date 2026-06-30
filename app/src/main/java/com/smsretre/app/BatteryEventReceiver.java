package com.smsretre.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BatteryEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        BatteryCheckJobService.schedulePeriodic(context);
        BatteryAlertManager.checkAndEnqueue(context);
    }
}
