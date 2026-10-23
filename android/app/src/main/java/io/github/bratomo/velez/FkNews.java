package io.github.bratomo.velez;

import android.content.Context;
import android.net.Uri;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Looks at the official club site (WordPress API) on every background run, so a new article
 * can be announced on the phone without waiting for the GitHub data file.
 * Articles are remembered by post id and by the news id used in docs/data/velez.json,
 * so the same article is never announced twice.
 */
final class FkNews {
    private FkNews() {}

    static final String POSTS_URL = "https://fkvelez.ba/wp-json/wp/v2/posts?per_page=10&_fields=id,date,link,title";

    /** Returns how many new articles were announced. */
    static int check(Context c, long now) {
        String txt = Net.get(POSTS_URL, 15000);
        if (txt == null) return 0;
        JSONArray arr;
        try {
            arr = new JSONArray(txt);
        } catch (Exception e) {
            return 0;
        }
        Set<String> seenPosts = Prefs.set(c, "seenPosts");
        Set<String> seenNews = Prefs.set(c, "seenNews");
        boolean first = seenPosts == null;
        Set<String> allPosts = seenPosts == null ? new HashSet<>() : new HashSet<>(seenPosts);
        Set<String> newsNow = seenNews == null ? new HashSet<>() : new HashSet<>(seenNews);
        List<String[]> fresh = new ArrayList<>(); // {postId, newsId, title}

        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p == null) continue;
            String pid = String.valueOf(p.optLong("id", 0));
            String nid = newsId(p.optString("link", ""), pid);
            if (pid.equals("0")) continue;
            allPosts.add(pid);
            if (first) continue;
            if (seenPosts.contains(pid) || newsNow.contains(nid)) continue;
            String title = titleOf(p);
            if (title.isEmpty()) continue;
            fresh.add(new String[]{pid, nid, title});
            newsNow.add(nid);
        }

        Prefs.putSet(c, "seenPosts", allPosts);
        // only extend the GitHub list once it exists; otherwise its first run would announce everything
        if (seenNews != null) Prefs.putSet(c, "seenNews", newsNow);
        if (fresh.isEmpty() || !Prefs.news(c)) return fresh.size();

        if (fresh.size() <= 3) {
            for (String[] f : fresh) {
                Notifier.show(c, Notifier.CH_NEWS, 61000 + (int) (Math.abs(Long.parseLong(f[0])) % 30000),
                        Data.cleanTitle(f[2]), "Nova objava na fkvelez.ba", null, "FK Velež", "vijesti");
            }
        } else {
            StringBuilder big = new StringBuilder();
            for (int i = 0; i < fresh.size() && i < 6; i++) {
                if (big.length() > 0) big.append('\n');
                big.append("• ").append(Data.cleanTitle(fresh.get(i)[2]));
            }
            String title = fresh.size() + " " + Data.plural(fresh.size(), "nova vijest", "nove vijesti", "novih vijesti") + " o FK Veležu";
            Notifier.show(c, Notifier.CH_NEWS, 61999, title, Data.cleanTitle(fresh.get(0)[2]), big.toString(), null, "vijesti");
        }
        return fresh.size();
    }

    /** The same id the GitHub data uses: "fkvelez-" plus the last part of the article address. */
    static String newsId(String link, String postId) {
        String seg = "";
        try {
            String path = Uri.parse(link).getPath();
            if (path != null) {
                String[] parts = path.split("/");
                for (int i = parts.length - 1; i >= 0; i--) {
                    if (!parts[i].isEmpty()) { seg = parts[i]; break; }
                }
            }
        } catch (RuntimeException ignored) {
            seg = "";
        }
        seg = seg.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        return seg.isEmpty() ? "fkvelez-post-" + postId : "fkvelez-" + seg;
    }

    private static String titleOf(JSONObject p) {
        JSONObject t = p.optJSONObject("title");
        String raw = t != null ? t.optString("rendered", "") : p.optString("title", "");
        return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }
}
