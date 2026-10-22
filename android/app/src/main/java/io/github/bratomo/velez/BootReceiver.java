package io.github.bratomo.velez;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the hourly check and the morning alarm after a restart, an update or a clock change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Scheduler.ensure(context);
        } catch (Throwable ignored) {
            // nothing else to do here
        }
    }
}
