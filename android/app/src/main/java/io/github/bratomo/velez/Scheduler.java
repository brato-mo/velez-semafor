package io.github.bratomo.velez;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Hourly background check (JobScheduler) and the morning alarm (AlarmManager). */
final class Scheduler {
    private Scheduler() {}

    static final int JOB_ID = 4201;

    static void ensure(Context c) {
        Notifier.channels(c);
        scheduleHourly(c);
        scheduleMorning(c);
    }

    static void scheduleHourly(Context ctx) {
        Context c = ctx.getApplicationContext();
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (js.getPendingJob(JOB_ID) != null) return; // keep the running schedule
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, CheckJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(60 * 60 * 1000L, 20 * 60 * 1000L)
                .setPersisted(true)
                .build();
        try {
            js.schedule(job);
        } catch (RuntimeException ignored) {
            // too many jobs or a vendor restriction; the app still checks when opened
        }
    }

    static long nextMorning(Context c, long now) {
        String[] p = Prefs.morningTime(c).split(":");
        Calendar cal = Calendar.getInstance(); // the phone's own time zone
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0]));
        cal.set(Calendar.MINUTE, Integer.parseInt(p[1]));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= now + 30_000L) cal.add(Calendar.DAY_OF_MONTH, 1);
        return cal.getTimeInMillis();
    }

    static void scheduleMorning(Context ctx) {
        Context c = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, MorningReceiver.class).setAction(MorningReceiver.ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (!Prefs.morning(c)) return;
        long at = nextMorning(c, System.currentTimeMillis());
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
        Prefs.get(c).edit().putLong("morningAt", at).apply();
    }
}
