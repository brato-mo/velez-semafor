package io.github.bratomo.velez;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires at the morning time (07:00 by default), posts the summary and arms the next day. */
public class MorningReceiver extends BroadcastReceiver {
    static final String ACTION = "io.github.bratomo.velez.MORNING";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();
        final Context c = context.getApplicationContext();
        new Thread(() -> {
            try {
                Checker.morning(c);
            } catch (Throwable ignored) {
                // never crash in the background
            } finally {
                Scheduler.scheduleMorning(c);
                result.finish();
            }
        }, "velez-morning").start();
    }
}
