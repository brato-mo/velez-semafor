package io.github.bratomo.velez;

import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/**
 * window.VelezApp in the page. Methods run on the WebView's bridge thread, so anything
 * that touches views is posted to the UI thread.
 */
final class Bridge {
    private final MainActivity a;

    Bridge(MainActivity activity) {
        a = activity;
    }

    @JavascriptInterface
    public String state() {
        return a.stateJson();
    }

    @JavascriptInterface
    public String set(String key, String value) {
        if (key == null || value == null) return a.stateJson();
        SharedPreferences.Editor e = Prefs.get(a).edit();
        switch (key) {
            case "news":
            case "results":
            case "reminder":
            case "morning":
                e.putBoolean(key, "true".equals(value));
                break;
            case "morningTime":
                if (!value.matches("\\d{2}:\\d{2}")) return a.stateJson();
                e.putString(key, value);
                break;
            default:
                return a.stateJson();
        }
        e.commit();
        if (key.startsWith("morning")) Scheduler.scheduleMorning(a);
        return a.stateJson();
    }

    @JavascriptInterface
    public void ready(String webVersion) {
        a.runOnUiThread(() -> a.onPageReady(webVersion));
    }

    @JavascriptInterface
    public void requestNotifications() {
        a.runOnUiThread(a::askNotifications);
    }

    @JavascriptInterface
    public String test() {
        if (!Notifier.enabled(a)) return "off";
        Notifier.show(a, Notifier.CH_NEWS, 9001, "Probna obavijest",
                "Obavijesti rade. Javit ću ti čim bude novosti o Veležu.", null, "Velež Semafor", "pregled");
        return "ok";
    }

    @JavascriptInterface
    public void checkNow() {
        new Thread(() -> {
            String msg;
            try {
                msg = Checker.check(a.getApplicationContext());
            } catch (Throwable t) {
                msg = "Provjera nije uspjela.";
            }
            final String m = msg;
            a.runOnUiThread(() -> a.js("window.VS&&VS.checked(" + JSONObject.quote(m) + ")"));
        }, "velez-check-now").start();
    }

    @JavascriptInterface
    public void battery() {
        a.runOnUiThread(a::openBatterySettings);
    }
}
