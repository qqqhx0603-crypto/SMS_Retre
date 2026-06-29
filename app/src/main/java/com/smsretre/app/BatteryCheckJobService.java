package com.smsretre.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BatteryCheckJobService extends JobService {
    private static final long FIFTEEN_MINUTES_MS = 15 * 60 * 1000L;
    private ExecutorService executor;

    @Override
    public boolean onStartJob(JobParameters params) {
        executor = Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    BatteryAlertManager.checkAndEnqueue(BatteryCheckJobService.this);
                } finally {
                    jobFinished(params, false);
                }
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (executor != null) {
            executor.shutdownNow();
        }
        return true;
    }

    static void schedulePeriodic(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        ComponentName componentName = new ComponentName(context, BatteryCheckJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(Constants.BATTERY_JOB_ID, componentName)
                .setPeriodic(FIFTEEN_MINUTES_MS)
                .setPersisted(true)
                .build();
        scheduler.schedule(jobInfo);
    }
}
