package io.github.bratomo.velez;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Notification channels and posting. */
final class Notifier {
    private Notifier() {}

    static final String CH_NEWS = "vijesti";
    static final String CH_RESULTS = "rezultati";
    static final String CH_MATCH = "utakmice";
    static final String CH_MORNING = "jutro";

    private static final String POST = "android.permission.POST_NOTIFICATIONS";

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(channel(CH_NEWS, "Nove vijesti", "Nova vijest o FK ili RK Veležu", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(channel(CH_RESULTS, "Rezultati", "Konačan rezultat utakmice sa strijelcima", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(channel(CH_MATCH, "Podsjetnik pred utakmicu", "Nekoliko sati prije početka utakmice", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(channel(CH_MORNING, "Jutarnji pregled", "Sljedeće utakmice i novosti od jučer", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private static NotificationChannel channel(String id, String name, String desc, int importance) {
        NotificationChannel ch = new NotificationChannel(id, name, importance);
        ch.setDescription(desc);
        return ch;
    }

    static boolean enabled(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(POST) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    /**
     * Posts one notification. {@code tab} is the app view to open when it is tapped
     * (pregled, vijesti, utakmice, tabela).
     */
    @SuppressWarnings("deprecation")
    static void show(Context c, String channel, int id, String title, String text, String bigText, String subText, String tab) {
        if (!enabled(c)) return;
        channels(c);
        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("tab", tab == null ? "pregled" : tab);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFFC3121E)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(pi);
        if (subText != null && !subText.isEmpty()) b.setSubText(subText);
        if (bigText != null && !bigText.isEmpty()) b.setStyle(new Notification.BigTextStyle().bigText(bigText));
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(CH_MATCH.equals(channel) ? Notification.PRIORITY_HIGH : Notification.PRIORITY_DEFAULT);
            b.setDefaults(Notification.DEFAULT_ALL);
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            nm.notify(id, b.build());
        } catch (SecurityException ignored) {
            // permission revoked between the check and the call
        }
    }
}
