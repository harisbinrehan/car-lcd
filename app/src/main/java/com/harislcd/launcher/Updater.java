package com.harislcd.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-app updates from this project's GitHub Releases. The repo is public, so no account or token is
 * needed and any internet connection works. A release carries an asset named car-lcd-<versionCode>.apk;
 * when that code is higher than the installed one, the home screen offers the update. The APK is
 * checked (same package, same signing key, expected version) before Android's installer is opened.
 */
final class Updater {
    private Updater() {}

    static final String REPO = "harisbinrehan/car-lcd";
    private static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final Pattern ASSET = Pattern.compile("car-lcd-(\\d+)\\.apk");
    private static final long CHECK_EVERY_MS = 3 * 60 * 60 * 1000L;     // GitHub allows 60 anonymous calls an hour
    private static final long SNOOZE_MS = 12 * 60 * 60 * 1000L;          // "Later" hides the offer this long
    private static final String ACTION_DONE = "com.harislcd.launcher.UPDATE_INSTALLED";

    static final class Release {
        int code;
        String name, notes, url;
        long size;
    }

    interface Result { void done(Release newer, String error); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean busy;
    // While the driver is away allowing installs, the release waits in prefs: Android restarts the app
    // when that permission changes, so it can't wait in memory. Cleared once the install carries on.
    private static void saveWaiting(Context c, Release r) {
        prefs(c).edit().putInt("upd_wait_code", r.code).putString("upd_wait_name", r.name)
                .putString("upd_wait_notes", r.notes).putString("upd_wait_url", r.url)
                .putLong("upd_wait_size", r.size).apply();
    }

    private static Release takeWaiting(Context c) {
        SharedPreferences p = prefs(c);
        int code = p.getInt("upd_wait_code", 0);
        if (code <= installedCode(c)) { p.edit().remove("upd_wait_code").apply(); return null; }
        Release r = new Release();
        r.code = code;
        r.name = p.getString("upd_wait_name", "");
        r.notes = p.getString("upd_wait_notes", "");
        r.url = p.getString("upd_wait_url", "");
        r.size = p.getLong("upd_wait_size", 0);
        p.edit().remove("upd_wait_code").apply();
        return r;
    }

    static int installedCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static String installedName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("carlcd", Context.MODE_PRIVATE); }

    /** Asks GitHub for the latest release, off the main thread; the answer comes back on the main thread. */
    static void check(Context c, Result r) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            Release newer = null;
            String err = null;
            try {
                String api = LATEST;
                // test builds only: point at a local copy of a release (set "upd_api" in prefs)
                if ((app.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                    api = prefs(app).getString("upd_api", LATEST);
                HttpURLConnection h = (HttpURLConnection) new URL(api).openConnection();
                h.setConnectTimeout(10000);
                h.setReadTimeout(15000);
                h.setRequestProperty("Accept", "application/vnd.github+json");
                h.setRequestProperty("User-Agent", "car-lcd-updater");
                int status = h.getResponseCode();
                if (status == 404) throw new Exception("No release published yet");
                if (status != 200) throw new Exception("GitHub answered " + status);
                JSONObject j = new JSONObject(read(h.getInputStream()));
                JSONArray assets = j.optJSONArray("assets");
                Release best = null;
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    Matcher m = ASSET.matcher(a.optString("name"));
                    if (!m.matches()) continue;
                    Release rel = new Release();
                    rel.code = Integer.parseInt(m.group(1));
                    rel.url = a.optString("browser_download_url");
                    rel.size = a.optLong("size");
                    rel.name = j.optString("tag_name").replaceFirst("^v", "");
                    rel.notes = j.optString("body").trim();
                    if (best == null || rel.code > best.code) best = rel;
                }
                prefs(app).edit().putLong("upd_checked", System.currentTimeMillis()).apply();
                if (best != null && best.code > installedCode(app)) newer = best;
            } catch (Exception e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            Diag.log("update", err != null ? "check failed: " + err : newer != null ? "version " + newer.name + " (" + newer.code + ") is available" : "up to date");
            final Release fn = newer;
            final String fe = err;
            MAIN.post(() -> r.done(fn, fe));
        }, "update-check").start();
    }

    /** Home screen: checks every few hours and offers a newer version, unless the driver said "Later". */
    static void maybeOffer(Activity a) {
        if (continueIfWaiting(a)) return;
        long last = prefs(a).getLong("upd_checked", 0);
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) return;
        check(a, (newer, err) -> {
            if (newer == null || a.isFinishing() || a.isDestroyed()) return;
            SharedPreferences p = prefs(a);
            boolean snoozed = p.getInt("upd_snooze_code", 0) == newer.code
                    && System.currentTimeMillis() - p.getLong("upd_snooze_at", 0) < SNOOZE_MS;
            if (!snoozed) offer(a, newer);
        });
    }

    /** Back from allowing installs: carry on with the update that was waiting. */
    static boolean continueIfWaiting(Activity a) {
        if (prefs(a).getInt("upd_wait_code", 0) == 0) return false;
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) return false;
        Release r = takeWaiting(a);
        if (r == null) return false;
        install(a, r);
        return true;
    }

    /** The "update available" dialog. Big buttons, works with touch and the remote. */
    static void offer(Activity a, Release r) {
        String notes = r.notes == null || r.notes.isEmpty() ? "" : "\n\nWhat's new:\n" + r.notes;
        AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle("Update available: version " + r.name)
                .setMessage("You have version " + installedName(a) + "." + notes)
                .setPositiveButton("Install now", (d, w) -> install(a, r))
                .setNegativeButton("Later", (d, w) -> prefs(a).edit()
                        .putInt("upd_snooze_code", r.code).putLong("upd_snooze_at", System.currentTimeMillis()).apply())
                .show();
        // large text and buttons: this is read and tapped in a car
        android.widget.TextView msg = dlg.findViewById(android.R.id.message);
        if (msg != null) msg.setTextSize(19);
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
            android.widget.Button b = dlg.getButton(which);
            if (b == null) continue;
            b.setTextSize(19);
            b.setMinHeight((int) (56 * a.getResources().getDisplayMetrics().density));
            b.setPadding(b.getPaddingLeft() * 2, b.getPaddingTop(), b.getPaddingRight() * 2, b.getPaddingBottom());
        }
        // remote: OK installs straight away (in touch mode no button holds focus, so the key comes here)
        final android.widget.Button go = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        if (go != null) {
            go.setFocusableInTouchMode(true);   // keeps focus even when opened by touch; already focused, so one tap still installs
            go.requestFocus();
        }
        dlg.setOnKeyListener((d, code, ev) -> {
            boolean ok = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER;
            android.view.View f = dlg.getCurrentFocus();
            if (!ok || go == null || (f != null && f.isFocused() && f instanceof android.widget.Button)) return false;
            if (ev.getAction() == android.view.KeyEvent.ACTION_UP) go.performClick();
            return true;
        });
    }

    /** Downloads, checks and hands the APK to Android's installer (which asks the driver to confirm). */
    static void install(Activity a, Release r) {
        if (busy) return;
        Diag.log("update", "install " + r.name + " (" + r.code + ") asked for");
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Diag.log("update", "installs from this app are not allowed yet, opening the setting");
            saveWaiting(a, r);
            Toast.makeText(a, "Turn on \"Allow from this source\", then press Back. The update continues by itself.", Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (Exception e) {
                a.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }
            return;
        }
        busy = true;
        final Context app = a.getApplicationContext();
        final Toast[] t = {Toast.makeText(app, "Downloading update " + r.name + "…", Toast.LENGTH_LONG)};
        t[0].show();
        new Thread(() -> {
            String err = null;
            try {
                File f = new File(app.getCacheDir(), "update.apk");
                download(r.url, f, r.size);
                verify(app, f, r.code);
                startInstaller(app, f);
                Diag.log("update", "downloaded and checked, handed to the installer");
            } catch (Exception e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Diag.log("update", "failed: " + e);
            } finally {
                busy = false;
            }
            final String fe = err;
            if (fe != null) MAIN.post(() -> Toast.makeText(app, "Update failed: " + fe, Toast.LENGTH_LONG).show());
        }, "update-install").start();
    }

    private static void download(String url, File out, long expected) throws Exception {
        HttpURLConnection h = null;
        for (int hop = 0; hop < 5; hop++) {          // GitHub redirects the asset to its download host
            h = (HttpURLConnection) new URL(url).openConnection();
            h.setInstanceFollowRedirects(false);
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            h.setRequestProperty("User-Agent", "car-lcd-updater");
            h.setRequestProperty("Accept", "application/octet-stream");
            int s = h.getResponseCode();
            if (s >= 300 && s < 400) { url = h.getHeaderField("Location"); h.disconnect(); continue; }
            if (s != 200) throw new Exception("download answered " + s);
            break;
        }
        try (InputStream in = h.getInputStream(); OutputStream o = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            long n = 0;
            for (int k; (k = in.read(buf)) > 0; ) { o.write(buf, 0, k); n += k; }
            if (expected > 0 && n != expected) throw new Exception("download incomplete");
        }
    }

    @SuppressWarnings("deprecation")
    private static void verify(Context c, File f, int code) throws Exception {
        PackageManager pm = c.getPackageManager();
        PackageInfo got = pm.getPackageArchiveInfo(f.getPath(), PackageManager.GET_SIGNATURES);
        if (got == null) throw new Exception("downloaded file is not an app");
        if (!c.getPackageName().equals(got.packageName)) throw new Exception("wrong app in the download");
        int gotCode = Build.VERSION.SDK_INT >= 28 ? (int) got.getLongVersionCode() : got.versionCode;
        if (gotCode != code) throw new Exception("version in the download does not match");
        Signature[] mine = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
        if (got.signatures == null || !Arrays.equals(mine, got.signatures))
            throw new Exception("download is not signed with this app's key");
    }

    private static void startInstaller(Context c, File f) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(c.getPackageName());
        sp.setSize(f.length());
        int id = pi.createSession(sp);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(f); OutputStream o = s.openWrite("car-lcd.apk", 0, f.length())) {
                byte[] buf = new byte[64 * 1024];
                for (int k; (k = in.read(buf)) > 0; ) o.write(buf, 0, k);
                s.fsync(o);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            Intent done = new Intent(c, Done.class).setAction(ACTION_DONE);
            s.commit(PendingIntent.getBroadcast(c, id, done, flags).getIntentSender());
        }
    }

    /** Gets the installer's answer: shows Android's confirm screen, or reports a failure. */
    public static class Done extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            Diag.log("update", "installer status " + status + " " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(c, "Update not installed" + (msg == null ? "" : ": " + msg), Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Right after an update the system shows whatever Home it falls back to; bring Car LCD back. */
    public static class Replaced extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) return;
            Diag.log("update", "updated to " + installedName(c) + " (" + installedCode(c) + ")");
            try {
                c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
            Toast.makeText(c, "Car LCD updated to version " + installedName(c), Toast.LENGTH_LONG).show();
        }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        for (int k; (k = in.read(buf)) > 0; ) b.write(buf, 0, k);
        in.close();
        return b.toString("UTF-8");
    }
}
