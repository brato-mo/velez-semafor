package io.github.bratomo.velez;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small HTTP and file helpers (no external libraries). */
final class Net {
    private Net() {}

    private static final int MAX_BYTES = 8 * 1024 * 1024;

    /** GET a URL as UTF-8 text. Returns null on any error or non-200 answer. */
    static String get(String url, int timeoutMs) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(timeoutMs);
            con.setReadTimeout(timeoutMs);
            con.setUseCaches(false);
            con.setRequestProperty("User-Agent", "VelezSemafor/" + BuildConfig.VERSION_NAME + " (Android)");
            con.setRequestProperty("Cache-Control", "no-cache");
            if (con.getResponseCode() != 200) return null;
            try (InputStream in = con.getInputStream()) {
                return readAll(in);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) throw new IOException("too large");
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static String readFile(Context c, String name) {
        File f = new File(c.getFilesDir(), name);
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        } catch (IOException e) {
            return null;
        }
    }

    static void writeFile(Context c, String name, String text) {
        File dir = c.getFilesDir();
        File tmp = new File(dir, name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        File dst = new File(dir, name);
        if (!tmp.renameTo(dst)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    static String readAsset(Context c, String name) {
        try (InputStream in = c.getAssets().open(name)) {
            return readAll(in);
        } catch (IOException e) {
            return null;
        }
    }
}
