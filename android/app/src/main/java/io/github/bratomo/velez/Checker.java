package io.github.bratomo.velez;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The background work: download the data file from GitHub, compare it with what the
 * phone saw last time and post notifications (new news, results, match reminders,
 * morning summary).
 */
final class Checker {
    private Checker() {}

    static final String DATA_URL = MainActivity.BASE + "data/velez.json";

    /** Downloads the data file (saving a copy). With allowCache, falls back to the saved copy. */
    static JSONObject fetch(Context c, boolean allowCache) {
        String txt = Net.get(DATA_URL + "?t=" + (System.currentTimeMillis() / 60000L), 15000);
        if (txt != null) {
            try {
                JSONObject d = new JSONObject(txt);
                if (d.optJSONArray("news") != null && d.optJSONArray("matches") != null) {
                    Net.writeFile(c, "data.json", txt);
                    return d;
                }
            } catch (Exception ignored) {
                // broken download: fall through
            }
        }
        if (!allowCache) return null;
        String cached = Net.readFile(c, "data.json");
        if (cached == null) return null;
        try {
            return new JSONObject(cached);
        } catch (Exception e) {
            return null;
        }
    }

    /** Hourly check. Returns a short Bosnian sentence for the app ("Nema novih vijesti."). */
    static synchronized String check(Context ctx) {
        Context c = ctx.getApplicationContext();
        long now = System.currentTimeMillis();
        JSONObject d = fetch(c, false);
        SharedPreferences.Editor e = Prefs.get(c).edit().putString("lastBg", Data.iso(now));
        if (d == null) {
            e.putString("lastBgText", "bez veze s internetom").apply();
            return "Nema veze s internetom.";
        }
        List<JSONObject> news = Data.list(d.optJSONArray("news"));
        List<JSONObject> matches = Data.list(d.optJSONArray("matches"));
        int n = newsCheck(c, news, now);
        int r = resultsCheck(c, matches, now);
        reminderCheck(c, matches, now);
        String txt = n == 0 ? "nema novih vijesti" : n + " " + Data.plural(n, "nova vijest", "nove vijesti", "novih vijesti");
        if (r > 0) txt += ", " + r + " " + Data.plural(r, "novi rezultat", "nova rezultata", "novih rezultata");
        e.putString("lastBgText", txt).apply();
        return Data.capital(txt) + ".";
    }

    private static int newsCheck(Context c, List<JSONObject> news, long now) {
        Set<String> seen = Prefs.set(c, "seenNews");
        boolean first = seen == null;
        Set<String> all = new HashSet<>();
        List<JSONObject> fresh = new ArrayList<>();
        for (JSONObject n : news) {
            String id = Data.s(n, "id");
            if (id.isEmpty()) continue;
            all.add(id);
            if (first || seen.contains(id)) continue;
            long added = Data.parseIso(Data.s(n, "added"));
            if (added > 0 && added < now - 3 * Data.DAY) continue; // old item filled in later: no alert
            fresh.add(n);
        }
        Prefs.putSet(c, "seenNews", all);
        if (fresh.isEmpty() || !Prefs.news(c)) return fresh.size();
        Collections.sort(fresh, (a, b) -> Data.s(b, "added").compareTo(Data.s(a, "added")));
        if (fresh.size() <= 3) {
            for (JSONObject n : fresh) {
                String title = Data.cleanTitle(Data.s(n, "title"));
                String sum = Data.s(n, "summary");
                String text = sum.isEmpty() ? Data.s(n, "source") : sum;
                Notifier.show(c, Notifier.CH_NEWS, ("n:" + Data.s(n, "id")).hashCode(), title, text,
                        sum.isEmpty() ? null : sum, Data.clubName(Data.s(n, "club")), "vijesti");
            }
        } else {
            StringBuilder big = new StringBuilder();
            for (int i = 0; i < fresh.size() && i < 6; i++) {
                if (big.length() > 0) big.append('\n');
                big.append("• ").append(Data.cleanTitle(Data.s(fresh.get(i), "title")));
            }
            String title = fresh.size() + " " + Data.plural(fresh.size(), "nova vijest", "nove vijesti", "novih vijesti") + " o Veležu";
            Notifier.show(c, Notifier.CH_NEWS, 7100, title, Data.cleanTitle(Data.s(fresh.get(0), "title")), big.toString(), null, "vijesti");
        }
        return fresh.size();
    }

    private static int resultsCheck(Context c, List<JSONObject> matches, long now) {
        Set<String> seen = Prefs.set(c, "seenResults");
        boolean first = seen == null;
        Set<String> all = new HashSet<>();
        int count = 0;
        for (JSONObject m : matches) {
            if (!"odigrano".equals(Data.s(m, "status")) || !Data.hasScore(m)) continue;
            JSONObject sc = m.optJSONObject("score");
            String key = Data.s(m, "id") + ":" + sc.optInt("h") + ":" + sc.optInt("a");
            all.add(key);
            if (first || seen.contains(key)) continue;
            long k = Data.kickoff(m);
            if (k > 0 && k < now - 4 * Data.DAY) continue; // correction of an old result: no alert
            count++;
            if (!Prefs.results(c)) continue;
            String title = "Kraj: " + Data.scoreLine(m);
            String info = Data.outcome(m) + " · " + Data.s(m, "comp") + ", " + Data.s(m, "stage");
            String goals = Data.scorers(m);
            Notifier.show(c, Notifier.CH_RESULTS, ("r:" + Data.s(m, "id")).hashCode(), title,
                    goals.isEmpty() ? info : goals, goals.isEmpty() ? info : info + "\n" + goals,
                    Data.clubName(Data.s(m, "club")), "utakmice");
        }
        Prefs.putSet(c, "seenResults", all);
        return count;
    }

    private static void reminderCheck(Context c, List<JSONObject> matches, long now) {
        if (!Prefs.reminder(c)) return;
        Set<String> done = Prefs.set(c, "reminded");
        if (done == null) done = new HashSet<>();
        boolean changed = false;
        for (JSONObject m : matches) {
            if (!"zakazano".equals(Data.s(m, "status")) || !Data.timed(m)) continue;
            String id = Data.s(m, "id");
            long k = Data.kickoff(m);
            if (k <= now || k - now > 3 * Data.HOUR || done.contains(id)) continue;
            String title = Data.capital(Data.dayLabel(Data.s(m, "date"), now)) + " u " + Data.s(m, "time") + ": " + Data.teams(m);
            StringBuilder text = new StringBuilder(Data.s(m, "comp")).append(", ").append(Data.s(m, "stage"));
            String venue = Data.s(m, "venue"), tv = Data.s(m, "tv");
            if (!venue.isEmpty()) text.append(" · ").append(venue);
            if (!tv.isEmpty()) text.append(" · ").append(tv);
            Notifier.show(c, Notifier.CH_MATCH, ("m:" + id).hashCode(), title, text.toString(), null,
                    Data.clubName(Data.s(m, "club")), "pregled");
            done.add(id);
            changed = true;
        }
        if (changed) Prefs.putSet(c, "reminded", done);
    }

    /** Morning summary: next matches, results since yesterday and news of the last 24 hours. */
    static synchronized void morning(Context ctx) {
        Context c = ctx.getApplicationContext();
        if (!Prefs.morning(c)) return;
        long now = System.currentTimeMillis();
        JSONObject d = fetch(c, true);
        String title = "Velež · " + Data.todayLong(now);
        if (d == null) {
            Notifier.show(c, Notifier.CH_MORNING, 7000, title, "Otvori aplikaciju za najnovije vijesti i raspored.", null, "Jutarnji pregled", "pregled");
            return;
        }
        List<JSONObject> matches = Data.list(d.optJSONArray("matches"));
        List<JSONObject> news = Data.list(d.optJSONArray("news"));
        StringBuilder big = new StringBuilder();
        String first = null;
        for (String club : new String[]{"fk", "rk"}) {
            String label = "fk".equals(club) ? "Fudbal" : "Rukomet";
            JSONObject last = lastResult(matches, club, now);
            if (last != null) {
                String goals = Data.scorers(last);
                line(big, label + ", kraj: " + Data.scoreLine(last) + (goals.isEmpty() ? "" : " (" + goals + ")"));
            }
            JSONObject next = Data.nextMatch(matches, club, now);
            String l = label + ": " + (next == null ? "nema zakazanih utakmica" : Data.matchLine(next, now));
            line(big, l);
            if (first == null) first = l;
        }
        List<JSONObject> fresh = new ArrayList<>();
        for (JSONObject n : news) {
            if (Data.parseIso(Data.s(n, "added")) > now - Data.DAY) fresh.add(n);
        }
        Collections.sort(fresh, (a, b) -> Data.s(b, "added").compareTo(Data.s(a, "added")));
        if (fresh.isEmpty()) {
            line(big, "Nema novih vijesti od jučer.");
        } else {
            line(big, "Novo:");
            for (int i = 0; i < fresh.size() && i < 4; i++) line(big, "• " + Data.cleanTitle(Data.s(fresh.get(i), "title")));
        }
        Notifier.show(c, Notifier.CH_MORNING, 7000, title, first, big.toString(), "Jutarnji pregled", "pregled");
        Prefs.get(c).edit().putString("lastMorning", Data.iso(now)).apply();
    }

    private static JSONObject lastResult(List<JSONObject> matches, String club, long now) {
        JSONObject best = null;
        long bestK = -1;
        for (JSONObject m : matches) {
            if (!club.equals(Data.s(m, "club")) || !"odigrano".equals(Data.s(m, "status")) || !Data.hasScore(m)) continue;
            long k = Data.kickoff(m);
            if (k < 0 || k < now - 30 * Data.HOUR || k > now) continue;
            if (k > bestK) {
                best = m;
                bestK = k;
            }
        }
        return best;
    }

    private static void line(StringBuilder b, String s) {
        if (b.length() > 0) b.append('\n');
        b.append(s);
    }
}
