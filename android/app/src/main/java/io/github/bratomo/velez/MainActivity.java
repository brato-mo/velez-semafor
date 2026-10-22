package io.github.bratomo.velez;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows the app page (docs/index.html from GitHub) in a WebView.
 *
 * Start-up: the last page that worked (or the copy inside the APK) is shown at once, then the
 * newest page is downloaded from GitHub and swapped in. A downloaded page must call
 * VelezApp.ready() within 15 seconds, otherwise the app goes back to the last working page,
 * so a broken update can never leave the app blank.
 */
public class MainActivity extends Activity {
    /** Where the app page and its data live: repository brato-mo/velez-semafor, branch main, folder docs. */
    static final String BASE = "https://raw.githubusercontent.com/brato-mo/velez-semafor/main/docs/";
    private static final String PAGE = BASE + "index.html";
    private static final String MARK = "name=\"velez-app\"";
    private static final Pattern VERSION = Pattern.compile("name=\"velez-version\"\\s+content=\"(\\d+)\"");
    private static final String POST = "android.permission.POST_NOTIFICATIONS";
    private static final int REQ_NOTIF = 11;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable watchdog = this::onWatchdog;
    private WebView web;
    private String shownHtml;
    private boolean shownIsCandidate;
    private boolean pageReady;
    private String pendingTab;
    private long lastPageFetch;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int bg = getColor(R.color.bg);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        web = new WebView(this);
        web.setBackgroundColor(bg);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        root.setOnApplyWindowInsetsListener(this::applyInsets);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString() + " VelezSemafor/" + BuildConfig.VERSION_NAME);
        web.addJavascriptInterface(new Bridge(this), "VelezApp");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri u = request.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
                if (scheme.equals("http") || scheme.equals("https")) {
                    // news and other web links open inside the app, in ArticleActivity (no app bridge there)
                    Intent a = new Intent(MainActivity.this, ArticleActivity.class);
                    a.putExtra(ArticleActivity.EXTRA_URL, u.toString());
                    startActivity(a);
                } else {
                    openExternal(u);
                }
                return true;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                recreate();
                return true;
            }
        });

        pendingTab = tabFrom(getIntent());
        showBest();
        fetchLatest();
        Scheduler.ensure(this);
    }

    // ---- page loading

    private static long version(String html) {
        if (html == null) return -1;
        Matcher m = VERSION.matcher(html);
        if (!m.find()) return 0;
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Shows the newer of: the last page that worked on this phone, the copy built into the APK. */
    private void showBest() {
        String good = Net.readFile(this, "good.html");
        String asset = Net.readAsset(this, "index.html");
        String html = version(good) >= version(asset) ? good : asset;
        if (html == null) html = asset;
        if (html == null) {
            html = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'>"
                    + "<body style='font:16px sans-serif;padding:24px'>Aplikacija se ne može otvoriti. Provjeri internet pa je pokreni ponovo.";
        }
        show(html, false);
    }

    private void show(String html, boolean candidate) {
        shownHtml = html;
        shownIsCandidate = candidate;
        pageReady = false;
        ui.removeCallbacks(watchdog);
        if (candidate) ui.postDelayed(watchdog, 15000);
        web.loadDataWithBaseURL(PAGE, html, "text/html", "utf-8", null);
    }

    private void fetchLatest() {
        lastPageFetch = System.currentTimeMillis();
        new Thread(() -> {
            final String html = Net.get(PAGE + "?t=" + (System.currentTimeMillis() / 60000L), 10000);
            if (html == null || !html.contains(MARK)) return;
            ui.post(() -> onLatest(html));
        }, "velez-page").start();
    }

    private void onLatest(String html) {
        if (isFinishing() || isDestroyed() || web == null) return;
        if (html.equals(shownHtml)) return;
        if (version(html) < version(shownHtml)) return; // the GitHub cache can lag behind for a few minutes
        if (String.valueOf(html.hashCode()).equals(Prefs.get(this).getString("badPage", ""))) return;
        show(html, true);
    }

    /** Called by the page (VelezApp.ready) once its script has started. */
    void onPageReady(String webVersion) {
        pageReady = true;
        ui.removeCallbacks(watchdog);
        shownIsCandidate = false;
        if (shownHtml != null && shownHtml.contains(MARK) && !shownHtml.equals(Net.readFile(this, "good.html"))) {
            Net.writeFile(this, "good.html", shownHtml);
        }
        if (pendingTab != null) {
            go(pendingTab);
            pendingTab = null;
        }
        SharedPreferences p = Prefs.get(this);
        if (!p.getBoolean("askedOnce", false)) {
            p.edit().putBoolean("askedOnce", true).apply();
            if (Build.VERSION.SDK_INT >= 33 && !Notifier.enabled(this)) ui.postDelayed(this::askNotifications, 1500);
        }
    }

    private void onWatchdog() {
        if (pageReady || !shownIsCandidate) return;
        // The new page did not start: remember it as broken and go back to the last one that worked.
        Prefs.get(this).edit().putString("badPage", String.valueOf(shownHtml.hashCode())).apply();
        showBest();
    }

    // ---- bridge helpers

    void js(String code) {
        if (web != null) web.evaluateJavascript(code, null);
    }

    private void go(String tab) {
        js("window.VS&&VS.go(" + JSONObject.quote(tab) + ")");
    }

    String stateJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("versionCode", BuildConfig.VERSION_CODE);
            o.put("versionName", BuildConfig.VERSION_NAME);
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("notif", Notifier.enabled(this) ? "on" : "off");
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            boolean free = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            o.put("battery", free ? "ok" : "limited");
            SharedPreferences p = Prefs.get(this);
            o.put("lastBg", p.getString("lastBg", ""));
            o.put("lastBgText", p.getString("lastBgText", ""));
            JSONObject s = new JSONObject();
            s.put("news", Prefs.news(this));
            s.put("results", Prefs.results(this));
            s.put("reminder", Prefs.reminder(this));
            s.put("morning", Prefs.morning(this));
            s.put("morningTime", Prefs.morningTime(this));
            o.put("s", s);
            return o.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(POST) != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences p = Prefs.get(this);
            int asked = p.getInt("notifAsked", 0);
            if (asked >= 2 && !shouldShowRequestPermissionRationale(POST)) {
                openNotificationSettings(); // Android no longer shows the dialog after two refusals
                return;
            }
            p.edit().putInt("notifAsked", asked + 1).apply();
            requestPermissions(new String[]{POST}, REQ_NOTIF);
            return;
        }
        if (!Notifier.enabled(this)) {
            openNotificationSettings();
            return;
        }
        js("window.VS&&VS.native()");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NOTIF) return;
        if (Notifier.enabled(this)) Scheduler.ensure(this);
        js("window.VS&&VS.native()");
    }

    private void openNotificationSettings() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 26) {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null));
        }
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("Otvori postavke telefona → Aplikacije → Velež Semafor → Obavijesti.");
        }
    }

    @SuppressLint("BatteryLife")
    void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (RuntimeException e2) {
                toast("Otvori postavke telefona → Baterija i dozvoli rad u pozadini za Velež Semafor.");
            }
        }
    }

    private void openExternal(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") && !scheme.equals("mailto") && !scheme.equals("tel")) return;
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("Nema aplikacije koja može otvoriti ovaj link.");
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    // ---- window insets (edge-to-edge on Android 15+)

    @SuppressWarnings("deprecation")
    private WindowInsets applyInsets(View v, WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            int[] p = Api30.bars(insets);
            v.setPadding(p[0], p[1], p[2], p[3]);
            return Api30.consumed();
        }
        v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
        return insets.consumeSystemWindowInsets();
    }

    private static final class Api30 {
        static int[] bars(WindowInsets insets) {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new int[]{i.left, i.top, i.right, i.bottom};
        }

        static WindowInsets consumed() {
            return WindowInsets.CONSUMED;
        }
    }

    // ---- lifecycle

    private static String tabFrom(Intent i) {
        if (i == null) return null;
        String t = i.getStringExtra("tab");
        return t == null || t.isEmpty() ? null : t;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String tab = tabFrom(intent);
        if (tab == null) return;
        if (pageReady) go(tab);
        else pendingTab = tab;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web == null) return;
        web.onResume();
        if (pageReady) js("window.VS&&VS.refresh('resume')");
        if (System.currentTimeMillis() - lastPageFetch > 60 * 60 * 1000L) fetchLatest();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (web == null || !pageReady) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript("(window.VS&&VS.back&&VS.back())?'1':'0'", v -> {
            if (!"\"1\"".equals(v)) exitBack();
        });
    }

    @SuppressWarnings("deprecation")
    private void exitBack() {
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (web != null) {
            web.removeJavascriptInterface("VelezApp");
            if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
