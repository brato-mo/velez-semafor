package io.github.bratomo.velez;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Reading docs/data/velez.json and turning matches into short Bosnian lines. */
final class Data {
    private Data() {}

    static final TimeZone TZ = TimeZone.getTimeZone("Europe/Sarajevo");
    static final long HOUR = 3600_000L;
    static final long DAY = 24 * HOUR;
    private static final String[] DAYS = {"ned", "pon", "uto", "sri", "čet", "pet", "sub"};
    private static final String[] DAYS_LONG = {"nedjelja", "ponedjeljak", "utorak", "srijeda", "četvrtak", "petak", "subota"};

    /** String field, "" for a missing or null value. */
    static String s(JSONObject o, String k) {
        return o == null || o.isNull(k) ? "" : o.optString(k, "");
    }

    static int round(JSONObject m) {
        return m.isNull("round") ? 99 : m.optInt("round", 99);
    }

    static List<JSONObject> list(JSONArray a) {
        List<JSONObject> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(o);
        }
        return out;
    }

    /** Kick-off in ms from the Sarajevo local date and time, or -1 when the date is unknown. */
    static long kickoff(JSONObject m) {
        String d = s(m, "date");
        if (!d.matches("\\d{4}-\\d{2}-\\d{2}")) return -1;
        String t = s(m, "time");
        int hh = 0, mm = 0;
        if (t.matches("\\d{1,2}:\\d{2}")) {
            String[] p = t.split(":");
            hh = Integer.parseInt(p[0]);
            mm = Integer.parseInt(p[1]);
        }
        Calendar c = Calendar.getInstance(TZ);
        c.clear();
        c.set(Integer.parseInt(d.substring(0, 4)), Integer.parseInt(d.substring(5, 7)) - 1, Integer.parseInt(d.substring(8, 10)), hh, mm, 0);
        return c.getTimeInMillis();
    }

    /** True when the exact day and time are officially known. */
    static boolean timed(JSONObject m) {
        return m.optBoolean("confirmed", false) && !s(m, "date").isEmpty() && !s(m, "time").isEmpty();
    }

    static String dayKey(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TZ);
        return f.format(new Date(ms));
    }

    /** "danas", "sutra", "jučer" or "ned 25.10.". */
    static String dayLabel(String ymd, long now) {
        if (ymd.equals(dayKey(now))) return "danas";
        if (ymd.equals(dayKey(now + DAY))) return "sutra";
        if (ymd.equals(dayKey(now - DAY))) return "jučer";
        try {
            Calendar c = Calendar.getInstance(TZ);
            c.clear();
            c.set(Integer.parseInt(ymd.substring(0, 4)), Integer.parseInt(ymd.substring(5, 7)) - 1, Integer.parseInt(ymd.substring(8, 10)), 12, 0, 0);
            return DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + c.get(Calendar.DAY_OF_MONTH) + "." + (c.get(Calendar.MONTH) + 1) + ".";
        } catch (RuntimeException e) {
            return ymd;
        }
    }

    /** "četvrtak, 23.10." */
    static String todayLong(long now) {
        Calendar c = Calendar.getInstance(TZ);
        c.setTimeInMillis(now);
        return DAYS_LONG[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH) + "." + (c.get(Calendar.MONTH) + 1) + ".";
    }

    static String capital(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    static String clubName(String club) {
        return "rk".equals(club) ? "RK Velež" : "FK Velež";
    }

    static String cleanTitle(String t) {
        return t == null ? "" : t.replaceFirst("(?i)^(IZVJEŠTAJ|NAJAVA|SLUŽBENO)\\s*:\\s*", "");
    }

    /** Next match of a club, using the same rule as the app page. */
    static JSONObject nextMatch(List<JSONObject> matches, String club, long now) {
        List<JSONObject> up = new ArrayList<>();
        for (JSONObject m : matches) {
            if (!club.equals(s(m, "club")) || !"zakazano".equals(s(m, "status"))) continue;
            long k = kickoff(m);
            if (k < 0 || !m.optBoolean("confirmed", false) || k > now - 3 * HOUR) up.add(m);
        }
        Collections.sort(up, (a, b) -> {
            long ka = kickoff(a), kb = kickoff(b);
            if (ka >= 0 && kb >= 0 && ka != kb) return Long.compare(ka, kb);
            if (ka >= 0 && kb < 0) return -1;
            if (kb >= 0 && ka < 0) return 1;
            return Integer.compare(round(a), round(b));
        });
        return up.isEmpty() ? null : up.get(0);
    }

    static String teams(JSONObject m) {
        return s(m, "home") + " – " + s(m, "away");
    }

    static String when(JSONObject m, long now) {
        String date = s(m, "date"), time = s(m, "time"), window = s(m, "window");
        if (date.isEmpty()) return "termin još nije objavljen";
        if (m.optBoolean("confirmed", false)) return dayLabel(date, now) + (time.isEmpty() ? "" : " u " + time);
        return "okvirno " + (window.isEmpty() ? dayLabel(date, now) : window);
    }

    /** "Velež – BSK, ned 25.10. u 20:00 · Stadion Rođeni, Mostar · MY TV" */
    static String matchLine(JSONObject m, long now) {
        StringBuilder b = new StringBuilder(teams(m)).append(", ").append(when(m, now));
        String venue = s(m, "venue"), tv = s(m, "tv");
        if (!venue.isEmpty()) b.append(" · ").append(venue);
        if (!tv.isEmpty()) b.append(" · ").append(tv);
        return b.toString();
    }

    static boolean hasScore(JSONObject m) {
        JSONObject sc = m.optJSONObject("score");
        return sc != null && sc.has("h") && sc.has("a");
    }

    /** "Velež 2:1 BSK" */
    static String scoreLine(JSONObject m) {
        JSONObject sc = m.optJSONObject("score");
        if (sc == null) return teams(m);
        return s(m, "home") + " " + sc.optInt("h") + ":" + sc.optInt("a") + " " + s(m, "away");
    }

    /** "Pobjeda", "Neriješeno" or "Poraz", from Velež's side. */
    static String outcome(JSONObject m) {
        JSONObject sc = m.optJSONObject("score");
        if (sc == null) return "";
        int h = sc.optInt("h"), a = sc.optInt("a");
        boolean home = "home".equals(s(m, "velez"));
        int v = home ? h : a, o = home ? a : h;
        return v > o ? "Pobjeda" : v < o ? "Poraz" : "Neriješeno";
    }

    /** Football scorers with minutes, or handball best scorers: "Velež: Rahimić 58'. BSK: Hodžić 12'". */
    static String scorers(JSONObject m) {
        List<JSONObject> goals = list(m.optJSONArray("goals"));
        List<JSONObject> top = list(m.optJSONArray("top"));
        if (goals.isEmpty() && top.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String side : new String[]{"h", "a"}) {
            StringBuilder part = new StringBuilder();
            if (!goals.isEmpty()) {
                Map<String, StringBuilder> by = new LinkedHashMap<>();
                for (JSONObject g : goals) {
                    if (!side.equals(s(g, "t"))) continue;
                    String n = s(g, "n");
                    StringBuilder mins = by.get(n);
                    if (mins == null) {
                        mins = new StringBuilder();
                        by.put(n, mins);
                    } else {
                        mins.append(", ");
                    }
                    mins.append(s(g, "m"));
                }
                for (Map.Entry<String, StringBuilder> e : by.entrySet()) {
                    if (part.length() > 0) part.append(", ");
                    part.append(e.getKey()).append(' ').append(e.getValue());
                }
            } else {
                for (JSONObject g : top) {
                    if (!side.equals(s(g, "t"))) continue;
                    if (part.length() > 0) part.append(", ");
                    part.append(s(g, "n")).append(' ').append(g.optInt("g"));
                }
            }
            if (part.length() == 0) continue;
            if (b.length() > 0) b.append(". ");
            b.append(s(m, "h".equals(side) ? "home" : "away")).append(": ").append(part);
        }
        return b.toString();
    }

    static String plural(int n, String one, String few, String many) {
        int a = n % 10, b = n % 100;
        if (a == 1 && b != 11) return one;
        if (a >= 2 && a <= 4 && (b < 12 || b > 14)) return few;
        return many;
    }

    static String iso(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    /** Parses "2026-10-22T14:05:00Z" (also without seconds or with fractions); -1 on failure. */
    static long parseIso(String s) {
        if (s == null || s.isEmpty()) return -1;
        String t = s.replaceFirst("\\.\\d+Z$", "Z");
        for (String p : new String[]{"yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm'Z'"}) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                f.setLenient(false);
                Date d = f.parse(t);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
                // try the next pattern
            }
        }
        return -1;
    }
}
