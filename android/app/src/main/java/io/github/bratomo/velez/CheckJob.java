package io.github.bratomo.velez;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Runs the hourly check off the main thread. */
public class CheckJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try {
                Checker.check(getApplicationContext());
            } catch (Throwable ignored) {
                // never crash in the background; the next run tries again
            }
            jobFinished(params, false);
        }, "velez-check").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // retry later
    }
}
