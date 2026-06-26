package com.smsretre.app;

import android.text.format.DateFormat;

import java.util.Date;
import java.util.Locale;

final class TimeUtils {
    private TimeUtils() {
    }

    static String full(long millis) {
        return DateFormat.format("yyyy-MM-dd HH:mm:ss", new Date(millis)).toString();
    }

    static String compact(long millis) {
        return DateFormat.format("yyyy-MM-dd HH:mm", new Date(millis)).toString();
    }

    static String durationSince(long startMillis, long nowMillis) {
        long seconds = Math.max(0L, (nowMillis - startMillis) / 1000L);
        return String.format(Locale.US, "%d:%02d", seconds / 60L, seconds % 60L);
    }
}
