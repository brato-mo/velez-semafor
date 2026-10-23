package io.github.bratomo.velez;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Shows a news article inside the app. Deliberately has NO JavaScript bridge (no addJavascriptInterface),
 * so the article website can never reach the app's functions.
 */
public class ArticleActivity extends Activity {
    static final String EXTRA_URL = "url";
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int bg = getColor(R.color.bg);
        int fg = getColor(R.color.board);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(10));
        bar.setBackgroundColor(bg);

        TextView close = new TextView(this);
        close.setText("✕ Zatvori");
        close.setTextColor(fg);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setTextSize(15);
        close.setPadding(dp(8), dp(6), dp(12), dp(6));
        close.setOnClickListener(v -> finish());
        bar.addView(close);

        TextView host = new TextView(this);
        host.setTextColor(Color.GRAY);
        host.setTextSize(13);
        host.setSingleLine(true);
        host.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(host, hp);

        web = new WebView(this);
        web.setBackgroundColor(bg);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
                boolean apk = u.getPath() != null && u.getPath().toLowerCase(Locale.ROOT).endsWith(".apk");
                if ((scheme.equals("http") || scheme.equals("https")) && !apk) return false;
                // mailto:, tel: and other apps: hand them to the phone
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(ArticleActivity.this, "Nema aplikacije koja može otvoriti ovaj link.", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(bg);
        column.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(column);

        String url = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_URL);
        Uri uri = url == null ? null : Uri.parse(url);
        boolean ok = uri != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        if (!ok) {
            finish();
            return;
        }
        host.setText(uri.getHost() == null ? "" : uri.getHost());
        if (state != null) web.restoreState(state);
        else web.loadUrl(uri.toString());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else finish();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
