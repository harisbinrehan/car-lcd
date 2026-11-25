package com.harislcd.launcher;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    // ---- the real apps on this unit (safe to change) ----
    private static final String PKG_RADIO    = "com.imotor.fmam";
    private static final String PKG_AUTO     = "com.imotor.phoneconnect";   // Android Auto / phone link
    private static final String PKG_YOUTUBE  = "com.google.android.youtube";
    private static final String PKG_EQ       = "com.imotor.soundequalizer";
    private static final String PKG_SETTINGS = "com.android.settings";
    private static final String PKG_MUSIC    = "com.imotor.music";
    private static final String PKG_BT       = "com.imotor.btmusic";

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    // default location (used until GPS gives a fix): Lahore
    private double lat = 31.52, lon = 74.36;   // city-level default until GPS has a fix

    private SensorManager sensorManager;
    private Sensor rotation;
    private LocationManager locationManager;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float[] rotMatrix = new float[9];
    private final float[] orientation = new float[3];
    private float smoothHeading = 0f;
    private boolean weatherLoaded = false;
    private float smoothSpeed = 0f;
    private int shownSpeed = 0;
    private String appliedMode = "classic";   // the theme this screen was built with

    private TextView txtTime, txtAmpm, txtGreeting, txtDate, txtSpeed, txtHeadingCard, txtHeadingDeg;
    private TextView txtTemp, txtCond, txtHigh, txtLow;
    private CompassView compass;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            refreshNowPlaying();
            ui.postDelayed(this, 1000);
        }
    };

    private final ContentObserver lightsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { /* handled by followLights(), which waits for a steady signal */ }
    };

    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Scale.wrap(base)); }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        appliedMode = ThemeStore.theme(this);
        setTheme(ThemeStore.style(appliedMode));
        ThemeStore.applyNight(this);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(ThemeStore.layout(appliedMode));
        Diag.log("home", "open, theme " + appliedMode + ", mode " + ThemeStore.mode(this) + ", " + (ThemeStore.isDark(this) ? "dark" : "light"));

        txtTime = findViewById(R.id.txt_time);
        txtAmpm = findViewById(R.id.txt_ampm);
        txtGreeting = findViewById(R.id.txt_greeting);
        txtDate = findViewById(R.id.txt_date);
        txtSpeed = findViewById(R.id.txt_speed);
        txtHeadingCard = findViewById(R.id.txt_heading_card);
        txtHeadingDeg = findViewById(R.id.txt_heading_deg);
        txtTemp = findViewById(R.id.txt_temp);
        txtCond = findViewById(R.id.txt_cond);
        txtHigh = findViewById(R.id.txt_high);
        txtLow = findViewById(R.id.txt_low);
        compass = findViewById(R.id.compass_view);
        if (compass != null) compass.setPosition(lat, lon);   // Qibla marker; the city-level default is close enough until GPS has a fix
        setupNowPlaying();

        // rail
        launchOn(R.id.item_music, PKG_MUSIC);
        findViewById(R.id.item_apps).setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        set(findViewById(R.id.txt_theme), "Themes");
        findViewById(R.id.item_theme).setOnClickListener(v -> startActivity(new Intent(this, ThemesActivity.class)));
        findViewById(R.id.item_settings).setOnClickListener(v -> openSettings());
        // long-press Settings: jump to Developer options (where Wireless debugging lives)
        findViewById(R.id.item_settings).setOnLongClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN)
                        .setClassName(PKG_SETTINGS, PKG_SETTINGS + ".Settings$DevelopmentSettingsDashboardActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Diag.err("home", "developer options", e);
                Toast.makeText(this, "Developer options not available", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        // dock (YouTube opens at any time; there is no parking lock)
        launchOn(R.id.tile_radio, PKG_RADIO);
        launchOn(R.id.tile_bt, PKG_BT);
        // these two have no normal launcher entry of their own, so open the exact screen
        componentOn(R.id.tile_auto, PKG_AUTO, PKG_AUTO + ".AndroidAuto");
        componentOn(R.id.tile_eq, PKG_EQ, PKG_EQ + ".MainActivity");
        launchOn(R.id.tile_youtube, PKG_YOUTUBE);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        ensureLocationPermission();
        updateClock();
        fetchWeather();
    }

    // ---------- themes ----------

    /** Not every theme's layout shows every field. */
    private static void set(TextView t, CharSequence s) {
        if (t != null && !s.toString().contentEquals(t.getText())) t.setText(s);   // no redraw when nothing changed
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---------- now playing ----------

    private MediaSessionManager sessions;
    private ComponentName listenerName;
    private MediaController player;          // whichever app is playing (or was last), null if none
    private TextView npTitle, npSub;
    private ImageView npPlay;
    private String npShown = "";

    private void setupNowPlaying() {
        npTitle = findViewById(R.id.np_title);
        if (npTitle != null) npTitle.setSelected(true);   // lets a long title scroll
        npSub = findViewById(R.id.np_sub);
        npPlay = findViewById(R.id.np_play);
        sessions = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        listenerName = new ComponentName(this, MediaListener.class);
        View prev = findViewById(R.id.np_prev), next = findViewById(R.id.np_next), root = findViewById(R.id.np_root);
        if (prev != null) prev.setOnClickListener(v -> { if (player != null) player.getTransportControls().skipToPrevious(); });
        if (next != null) next.setOnClickListener(v -> { if (player != null) player.getTransportControls().skipToNext(); });
        if (npPlay != null) npPlay.setOnClickListener(v -> {
            if (player == null) { launch(PKG_MUSIC); return; }
            PlaybackState st = player.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) player.getTransportControls().pause();
            else player.getTransportControls().play();
        });
        if (root != null) root.setOnClickListener(v -> openPlayer());
    }

    /**
     * Opens the screen of whatever is playing. A media session can belong to a background app with no
     * launcher entry (the unit's Android Auto host, com.carletter.car), so try the session's own screen
     * first, then the app's launcher entry, then Android Auto for the phone-link apps, then Music.
     */
    private void openPlayer() {
        MediaController p = player;
        if (p == null) { launch(PKG_MUSIC); return; }
        String pkg = p.getPackageName();
        try {
            android.app.PendingIntent pi = p.getSessionActivity();
            if (pi != null) { pi.send(); Diag.log("player", "open " + pkg + " by its own screen"); return; }
        } catch (Exception e) { Diag.err("player", "own screen of " + pkg, e); }
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) {
            try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); Diag.log("player", "open " + pkg); return; }
            catch (Exception e) { Diag.err("player", "open " + pkg, e); }
        }
        if (pkg.startsWith("com.carletter") || pkg.equals(PKG_AUTO)) {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).setClassName(PKG_AUTO, PKG_AUTO + ".AndroidAuto")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Diag.log("player", "open Android Auto for " + pkg);
                return;
            } catch (Exception e) { Diag.err("player", "Android Auto for " + pkg, e); }
        }
        Diag.log("player", "no screen for " + pkg + ", opening Music");
        launch(PKG_MUSIC);
    }

    /** Called once a second: shows the app that is playing, its track and artist, and play/pause. */
    private void refreshNowPlaying() {
        if (npTitle == null || sessions == null) return;
        MediaController best = null;
        try {
            // whatever is playing wins; otherwise the first source that could be started
            // (a disconnected Bluetooth source reports an error state and is skipped)
            int bestScore = -1;
            for (MediaController c : sessions.getActiveSessions(listenerName)) {
                PlaybackState st = c.getPlaybackState();
                int state = st == null ? PlaybackState.STATE_NONE : st.getState();
                int score = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING ? 3
                        : state == PlaybackState.STATE_PAUSED ? 2
                        : state == PlaybackState.STATE_ERROR ? 0 : 1;
                if (score > bestScore) { bestScore = score; best = c; }
            }
            if (bestScore == 0) best = null;
            Diag.logChange("np-access", "now-playing access ok");
        } catch (SecurityException e) {
            best = null;   // notification access has not been granted to the launcher
            Diag.logChange("np-access", "no now-playing access (notification listener not allowed)");
        }
        player = best;
        String title = "Nothing playing", sub = "Tap play to open Music";
        boolean playing = false;
        if (best != null) {
            MediaMetadata md = best.getMetadata();
            CharSequence t = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
            if ((t == null || t.length() == 0) && md != null) t = md.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            CharSequence a = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_ARTIST);
            if ((a == null || a.length() == 0) && md != null) a = md.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            String app = appLabel(best.getPackageName());
            boolean hasTitle = t != null && t.length() > 0;
            title = hasTitle ? t.toString() : app;
            PlaybackState ps = best.getPlaybackState();
            boolean live = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
            sub = a != null && a.length() > 0 ? a + " · " + app : (hasTitle ? app : (live ? "Playing" : "Tap play to start"));
            PlaybackState st = best.getPlaybackState();
            playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        }
        String key = title + "|" + sub + "|" + playing;
        if (key.equals(npShown)) return;
        npShown = key;
        Diag.log("np", best == null ? "nothing playing" : best.getPackageName() + (playing ? " playing: " : " paused: ") + title);
        npTitle.setText(title);
        set(npSub, sub);
        if (npPlay != null) npPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private String appLabel(String pkg) {
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    // ---------- app launching ----------

    private void launchOn(int viewId, final String pkg) {
        View v = findViewById(viewId);
        if (v != null) v.setOnClickListener(x -> launch(pkg));
    }

    private void componentOn(int viewId, final String pkg, final String cls) {
        View v = findViewById(viewId);
        if (v == null) return;
        v.setOnClickListener(x -> {
            Intent i = new Intent(Intent.ACTION_MAIN).setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
                Diag.log("open", cls);
            } catch (Exception e) {
                Diag.err("open", cls, e);
                launch(pkg);
            }
        });
    }

    private void launch(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Diag.log("open", pkg + " is not installed");
            Toast.makeText(this, "App not installed: " + pkg, Toast.LENGTH_SHORT).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            Diag.log("open", pkg);
        } catch (Exception e) {
            Diag.err("open", pkg, e);
            Toast.makeText(this, "Can't open that app", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        Intent i = getPackageManager().getLaunchIntentForPackage(PKG_SETTINGS);
        if (i == null) i = new Intent(Settings.ACTION_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); Diag.log("open", "settings"); } catch (Exception e) { Diag.err("open", "settings", e); }
    }

    /**
     * Auto mode follows the car's lights, but the unit's lights signal can blip for a moment.
     * Only switch once the new state has held for two checks in a row (about two seconds).
     */
    private int lightsStreak = 0;

    private void followLights() {
        boolean want = ThemeStore.isDark(this);
        boolean now = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()
                == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
        if (want == now) { lightsStreak = 0; return; }
        if (++lightsStreak >= 2) {
            lightsStreak = 0;
            Diag.log("lights", "switching to " + (want ? "dark" : "light") + ", mode " + ThemeStore.mode(this)
                    + ", lights signal " + (ThemeStore.lightsOn(this) ? 1 : 0));
            ThemeStore.applyNight(this);
        }
    }

    // ---------- clock ----------

    private void updateClock() {
        followLights();
        Date now = new Date();
        set(txtTime, new SimpleDateFormat("h:mm", Locale.US).format(now));
        set(txtAmpm, new SimpleDateFormat("a", Locale.US).format(now).toUpperCase(Locale.US));
        set(txtDate, new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now));
        int hour = Integer.parseInt(new SimpleDateFormat("H", Locale.US).format(now));
        String part = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : hour < 21 ? "Good evening" : "Good night";
        set(txtGreeting, part);
    }

    // ---------- location / speed ----------

    private void ensureLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            startLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocation();
        }
    }

    private void startLocation() {
        if (locationManager == null) return;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0f, this);
            }
            Diag.log("gps", "listening, gps " + (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? "on" : "off"));
        } catch (SecurityException e) { Diag.err("gps", "listening", e); }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        lat = location.getLatitude();
        lon = location.getLongitude();
        if (compass != null) compass.setPosition(lat, lon);
        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            Diag.logChange("gps-fix", "gps has a fix");   // the position itself is never written down
            float kmh = steadySpeed(location.hasSpeed() ? location.getSpeed() * 3.6f : 0f);
            if (shownSpeed != (int) kmh) {
                shownSpeed = (int) kmh;
                set(txtSpeed, String.valueOf(shownSpeed));
            }
            // Head units often have no magnetometer: use the GPS direction of travel instead.
            if (rotation == null && location.hasBearing() && kmh > 3f) showHeading(location.getBearing());
        }
        if (!weatherLoaded) fetchWeather();
    }

    /**
     * GPS speed wanders by a km/h or two even when the car is standing still. This turns the raw
     * reading into a steady whole number: crawl speeds count as stopped, readings are averaged,
     * and the shown number only moves once the average is clearly past the next value.
     */
    private float steadySpeed(float rawKmh) {
        if (rawKmh < 3f) rawKmh = 0f;                       // GPS noise while stationary
        smoothSpeed += (rawKmh - smoothSpeed) * 0.4f;       // running average
        if (rawKmh == 0f && smoothSpeed < 1.5f) smoothSpeed = 0f;
        float shown = shownSpeed;
        if (Math.abs(smoothSpeed - shown) >= 0.8f) shown = Math.round(smoothSpeed);
        return shown;
    }

    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    // ---------- compass ----------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values);
        SensorManager.getOrientation(rotMatrix, orientation);
        float az = ((float) Math.toDegrees(orientation[0]) + 360f) % 360f;
        // smooth, handling the 0/360 wrap
        float delta = ((az - smoothHeading + 540f) % 360f) - 180f;
        smoothHeading = (smoothHeading + delta * 0.15f + 360f) % 360f;
        showHeading(smoothHeading);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void showHeading(float deg) {
        if (compass != null) compass.setHeading(deg);
        int d = Math.round(deg) % 360;
        set(txtHeadingDeg, d + "°");
        set(txtHeadingCard, DIRS[Math.round(deg / 45f) % 8]);
    }

    // ---------- weather ----------

    private void fetchWeather() {
        new WeatherClient().fetch(lat, lon, new WeatherClient.Callback() {
            @Override
            public void onWeather(int tempC, int highC, int lowC, String condition, int weatherCode) {
                weatherLoaded = true;
                Diag.logChange("weather", "loaded");
                set(txtTemp, tempC + "°");
                set(txtCond, condition);
                set(txtHigh, highC + "°");
                set(txtLow, lowC + "°");
            }
            @Override public void onError() {
                Diag.logChange("weather", "not loaded, will keep trying");
                // no network yet (common just after the unit boots): try again shortly
                ui.postDelayed(() -> { if (!weatherLoaded) fetchWeather(); }, 20000);
            }
        });
    }

    // ---------- lifecycle ----------

    @Override
    protected void onResume() {
        super.onResume();
        if (!ThemeStore.theme(this).equals(appliedMode)) { recreate(); return; }   // changed on the Themes page
        Diag.log("home", "shown");
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(ThemeStore.LIGHTS_SETTING), false, lightsObserver);
        ui.post(clockTick);
        Updater.maybeOffer(this);   // looks for a newer release every few hours
        if (sensorManager != null && rotation != null) {
            sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Diag.log("home", "left");
        getContentResolver().unregisterContentObserver(lightsObserver);
        ui.removeCallbacks(clockTick);
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
        }
    }

    // A home screen ignores Back.
    @Override
    public void onBackPressed() { }
}
