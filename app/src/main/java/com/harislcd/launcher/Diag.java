package com.harislcd.launcher;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The app's own diary, kept on the unit so a bug can be looked at afterwards: what was opened, theme and
 * lights changes, what Now Playing saw, update checks, errors and crash traces. Nothing leaves the unit
 * until "Send report" is tapped on the Themes page; that files the diary as an issue in a private
 * GitHub repo. The GPS position is never written here.
 */
final class Diag {
    private Diag() {}

    static final String REPO = "harisbinrehan/car-lcd-reports";
    private static final String ISSUES = "https://api.github.com/repos/" + REPO + "/issues";
    private static final long MAX_LOG = 200 * 1024;      // then the file becomes diag.1.log and a new one starts
    private static final long MAX_CRASH = 48 * 1024;
    private static final int SEND_LOG = 42000, SEND_CRASH = 14000;   // an issue body holds 65536 characters

    interface Result { void done(String sent, String error); }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "diag");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final HashMap<String, String> LAST = new HashMap<>();
    private static volatile File dir;
    private static volatile boolean sending;

    /** Called once when the app starts: opens the diary and catches crashes. */
    static void init(Context app) {
        dir = app.getFilesDir();
        final Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                // written straight away, on this thread: the app is about to be killed
                String text = stamp() + " version " + Updater.installedName(app) + " (" + Updater.installedCode(app)
                        + "), thread " + thread.getName() + "\n" + trace(e) + "\n";
                File f = new File(dir, "crash.txt");
                if (f.length() > MAX_CRASH) f.delete();
                append(f, text);
                append(new File(dir, "diag.log"), stamp() + " crash: " + e + "\n");
            } catch (Throwable ignored) { }
            if (before != null) before.uncaughtException(thread, e);
        });
        log("app", "start version " + Updater.installedName(app) + " (" + Updater.installedCode(app) + ")"
                + (debuggable(app) ? " debug" : ""));
    }

    static void log(String tag, String msg) {
        if (dir == null) return;
        final String line = stamp() + " " + tag + ": " + msg + "\n";
        IO.execute(() -> {
            try {
                File f = new File(dir, "diag.log");
                if (f.length() > MAX_LOG) {
                    File old = new File(dir, "diag.1.log");
                    old.delete();
                    f.renameTo(old);
                }
                append(f, line);
            } catch (Throwable ignored) { }
        });
    }

    /** For things checked over and over (every second, every retry): written only when the answer changes. */
    static void logChange(String tag, String msg) {
        synchronized (LAST) {
            if (msg.equals(LAST.get(tag))) return;
            LAST.put(tag, msg);
        }
        log(tag, msg);
    }

    static void err(String tag, String what, Throwable e) {
        log(tag, what + " failed: " + e);
    }

    static boolean canSend() { return !token().isEmpty(); }

    /** Files the diary as an issue in the private reports repo. The answer comes back on the main thread. */
    static void send(Context c, Result r) {
        final Context app = c.getApplicationContext();
        if (sending) return;
        sending = true;
        log("report", "sending");
        IO.execute(() -> {            // same thread as the writer, so the diary is complete and not mid-line
            String sent = null, err = null;
            try {
                String api = ISSUES;
                // test builds only: point at a local test server (set "rep_api" in prefs)
                if (debuggable(app)) api = prefs(app).getString("rep_api", ISSUES);
                String token = token();
                if (token.isEmpty() && api.equals(ISSUES)) throw new Exception("reports are not set up in this build");
                JSONObject j = new JSONObject();
                j.put("title", "Report " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date())
                        + " · version " + Updater.installedName(app));
                j.put("body", body(app));
                byte[] out = j.toString().getBytes(StandardCharsets.UTF_8);
                HttpURLConnection h = (HttpURLConnection) new URL(api).openConnection();
                h.setConnectTimeout(15000);
                h.setReadTimeout(30000);
                h.setRequestMethod("POST");
                h.setDoOutput(true);
                h.setFixedLengthStreamingMode(out.length);
                h.setRequestProperty("Accept", "application/vnd.github+json");
                h.setRequestProperty("Content-Type", "application/json");
                h.setRequestProperty("User-Agent", "car-lcd-reports");
                if (!token.isEmpty()) h.setRequestProperty("Authorization", "Bearer " + token);
                try (OutputStream o = h.getOutputStream()) { o.write(out); }
                int status = h.getResponseCode();
                if (status == 401 || status == 403 || status == 404) throw new Exception("GitHub refused the report key (" + status + ")");
                if (status != 201) throw new Exception("GitHub answered " + status);
                sent = "number " + new JSONObject(read(h.getInputStream())).optInt("number");
            } catch (java.net.UnknownHostException | java.net.ConnectException | java.net.SocketTimeoutException e) {
                err = "no internet. The report is kept, try again when connected";
            } catch (Exception e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } finally {
                sending = false;
            }
            log("report", sent != null ? "sent, " + sent : "not sent: " + err);
            final String fs = sent, fe = err;
            MAIN.post(() -> r.done(fs, fe));
        });
    }

    // ---------- what a report holds ----------

    private static String body(Context c) {
        StringBuilder b = new StringBuilder();
        b.append("**App** ").append(Updater.installedName(c)).append(" (").append(Updater.installedCode(c)).append(")")
                .append(debuggable(c) ? " debug" : "").append('\n');
        b.append("**Unit** ").append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        b.append("**Theme** ").append(ThemeStore.theme(c)).append(", mode ").append(ThemeStore.mode(c))
                .append(", showing ").append(ThemeStore.isDark(c) ? "dark" : "light")
                .append(", lights signal ").append(setting(c, ThemeStore.LIGHTS_SETTING)).append('\n');
        TimeZone tz = TimeZone.getDefault();
        b.append("**Clock** ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
                .append(", zone ").append(tz.getID()).append(" (UTC").append(String.format(Locale.US, "%+.1f", tz.getOffset(System.currentTimeMillis()) / 3600000f))
                .append("), automatic time ").append(global(c, "auto_time")).append(", automatic zone ").append(global(c, "auto_time_zone"))
                .append(", 12/24 ").append(setting(c, Settings.System.TIME_12_24)).append('\n');
        b.append("**State** internet ").append(online(c) ? "yes" : "no")
                .append(", now-playing access ").append(listenerOn(c) ? "yes" : "no")
                .append(", location permission ").append(c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ? "yes" : "no")
                .append(", running ").append(SystemClock.elapsedRealtime() / 60000).append(" min since boot")
                .append(", memory free ").append(Runtime.getRuntime().freeMemory() / 1048576).append(" of ")
                .append(Runtime.getRuntime().maxMemory() / 1048576).append(" MB\n");
        String crash = tail(new File(dir, "crash.txt"), SEND_CRASH);
        b.append("\n### Crashes\n").append(crash.isEmpty() ? "none\n" : fence(crash));
        String log = tail(new File(dir, "diag.log"), SEND_LOG);
        if (log.length() < SEND_LOG / 2) log = tail(new File(dir, "diag.1.log"), SEND_LOG - log.length()) + log;
        b.append("\n### Log\n").append(fence(log));
        return b.toString();
    }

    private static String fence(String s) { return "```\n" + s.replace("```", "'''") + (s.endsWith("\n") ? "" : "\n") + "```\n"; }

    /** The end of a file, starting on a whole line. */
    private static String tail(File f, int max) {
        if (max <= 0 || !f.exists()) return "";
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long from = Math.max(0, r.length() - max);
            byte[] buf = new byte[(int) (r.length() - from)];
            r.seek(from);
            r.readFully(buf);
            String s = new String(buf, StandardCharsets.UTF_8);
            if (from > 0) s = s.substring(s.indexOf('\n') + 1);
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- helpers ----------

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("carlcd", Context.MODE_PRIVATE); }

    private static boolean debuggable(Context c) { return (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0; }

    /** The reports key is put in at build time (see CLAUDE.md), lightly scrambled so it is not plain text in the APK. */
    private static String token() {
        String hex = BuildConfig.REPORT_KEY;
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) (Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16) ^ 0x5A);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static String stamp() { return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()); }

    private static String trace(Throwable e) {
        StringWriter w = new StringWriter();
        e.printStackTrace(new PrintWriter(w));
        return w.toString();
    }

    private static void append(File f, String text) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f, true)) { o.write(text.getBytes(StandardCharsets.UTF_8)); }
    }

    private static String setting(Context c, String name) {
        String v = Settings.System.getString(c.getContentResolver(), name);
        return v == null ? "not set" : v;
    }

    private static String global(Context c, String name) {
        String v = Settings.Global.getString(c.getContentResolver(), name);
        return v == null ? "not set" : "1".equals(v) ? "on" : "off";
    }

    private static boolean online(Context c) {
        try {
            NetworkInfo n = ((ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean listenerOn(Context c) {
        String on = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return on != null && on.contains(c.getPackageName() + "/");
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        for (int k; (k = in.read(buf)) > 0; ) b.write(buf, 0, k);
        in.close();
        return b.toString("UTF-8");
    }
}
