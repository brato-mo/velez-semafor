package io.github.bratomo.velez;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** User settings and the small bits of state the background check keeps between runs. */
final class Prefs {
    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences("velez", Context.MODE_PRIVATE);
    }

    static boolean news(Context c) { return get(c).getBoolean("news", true); }

    static boolean results(Context c) { return get(c).getBoolean("results", true); }

    static boolean reminder(Context c) { return get(c).getBoolean("reminder", true); }

    static boolean morning(Context c) { return get(c).getBoolean("morning", true); }

    static String morningTime(Context c) {
        String t = get(c).getString("morningTime", "07:00");
        return t != null && t.matches("\\d{2}:\\d{2}") ? t : "07:00";
    }

    /** A copy of a stored string set, or null when it was never stored (first run). */
    static Set<String> set(Context c, String key) {
        Set<String> s = get(c).getStringSet(key, null);
        return s == null ? null : new HashSet<>(s);
    }

    static void putSet(Context c, String key, Set<String> value) {
        get(c).edit().putStringSet(key, new HashSet<>(value)).apply();
    }
}
