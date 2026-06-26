package com.smsretre.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ForwardJobService extends JobService {
    private static final String TAG = "ForwardJobService";
    private static final int JOB_ID = 1962001;

    private ExecutorService executor;

    @Override
    public boolean onStartJob(JobParameters params) {
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            boolean needsReschedule = false;
            try {
                QueueProcessor.process(this);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                needsReschedule = true;
            } catch (Exception e) {
                Log.e(TAG, "Job queue processing failed", e);
                needsReschedule = true;
            } finally {
                jobFinished(params, needsReschedule);
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

    static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        ComponentName componentName = new ComponentName(context, ForwardJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(0L)
                .setOverrideDeadline(Constants.RETRY_INTERVAL_MS)
                .build();
        scheduler.schedule(jobInfo);
    }
}
