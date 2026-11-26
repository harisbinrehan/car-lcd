package com.harislcd.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * The compass, big: opened by tapping the small one on the home screen. Shows the same dial across the
 * height of the screen, the heading in large letters, and how far to turn to face the Qibla.
 */
public class CompassActivity extends AppCompatActivity implements LocationListener, SensorEventListener {

    static final String HEADING = "heading", KNOWN = "known", LAT = "lat", LON = "lon";
    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private CompassView compass;
    private TextView txtCard, txtDeg, txtQibla, txtTurn;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor rotation;
    private final float[] rotMatrix = new float[9], orientation = new float[3];
    private float heading;
    private boolean known;      // no heading yet: the unit has no compass sensor and the car has not moved

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void attachBaseContext(Context base) { super.attachBaseContext(Scale.wrap(base)); }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(ThemeStore.style(ThemeStore.theme(this)));
        ThemeStore.applyNight(this);
        super.onCreate(savedInstanceState);
        int fg = ThemeStore.color(this, R.attr.cFg), muted = ThemeStore.color(this, R.attr.cMuted);
        int accent = ThemeStore.color(this, R.attr.cAccent);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.screen_bg);
        root.setPadding(dp(28), dp(16), dp(28), dp(16));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setOnClickListener(v -> finish());
        back.setBackgroundResource(R.drawable.focus_bg);
        back.setFocusable(true);
        back.setContentDescription("Back");
        head.addView(back, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView title = new TextView(this);
        title.setText("COMPASS");
        title.setTextColor(fg);
        title.setTextSize(26);
        title.setLetterSpacing(0.1f);
        title.setPadding(dp(10), 0, 0, 0);
        head.addView(title);
        root.addView(head);

        LinearLayout body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER);
        compass = new CompassView(this, null);
        compass.setOnClickListener(v -> finish());              // a tap on the dial goes back, like it opened
        body.addView(compass, new LinearLayout.LayoutParams(dp(520), dp(520)));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER_VERTICAL);
        side.setPadding(dp(64), 0, 0, 0);
        txtCard = text(side, 120, fg, true);
        txtDeg = text(side, 64, fg, false);
        TextView gap = text(side, 22, muted, false);
        gap.setText(" ");
        txtQibla = text(side, 30, accent, true);
        txtTurn = text(side, 30, fg, false);
        TextView note = text(side, 20, muted, false);
        note.setPadding(0, dp(18), 0, 0);
        body.addView(side, new LinearLayout.LayoutParams(dp(520), LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        back.requestFocus();

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotation = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        note.setText(rotation == null ? "The heading comes from GPS, so it updates while the car is moving."
                : "N is up; the needle shows where the car is heading.");

        // start from what the home screen was showing
        compass.setPosition(getIntent().getDoubleExtra(LAT, 0), getIntent().getDoubleExtra(LON, 0));
        known = getIntent().getBooleanExtra(KNOWN, false);
        heading = getIntent().getFloatExtra(HEADING, 0f);
        if (known) show(heading); else unknown();
        Diag.log("compass", "opened");
    }

    private TextView text(LinearLayout into, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        into.addView(t);
        return t;
    }

    /** Before the first heading: the dial points north, and the text says why there is no reading. */
    private void unknown() {
        compass.setHeading(0f);
        txtCard.setText("--");
        txtDeg.setText("");
        float q = compass.qibla();
        txtQibla.setText(Float.isNaN(q) ? "" : "Qibla " + Math.round(q) % 360 + "° " + DIRS[Math.round(q / 45f) % 8]);
        txtTurn.setText("Drive a little to get the heading");
    }

    private void show(float deg) {
        known = true;
        heading = (deg % 360f + 360f) % 360f;
        compass.setHeading(heading);
        int d = Math.round(heading) % 360;
        txtCard.setText(DIRS[Math.round(heading / 45f) % 8]);
        txtDeg.setText(d + "°");
        float q = compass.qibla();
        if (Float.isNaN(q)) {
            txtQibla.setText("");
            txtTurn.setText("");
            return;
        }
        txtQibla.setText("Qibla " + Math.round(q) % 360 + "° " + DIRS[Math.round(q / 45f) % 8]);
        int turn = Math.round(((q - heading + 540f) % 360f) - 180f);       // -180..180, right is positive
        txtTurn.setText(Math.abs(turn) <= 3 ? "Facing the Qibla"
                : "Turn " + (turn > 0 ? "right " : "left ") + Math.abs(turn) + "°");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && rotation != null) sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI);
        if (locationManager == null) return;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, this);
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0f, this);
        } catch (SecurityException ignored) { }                         // no location permission: the dial stays as it opened
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        compass.setPosition(location.getLatitude(), location.getLongitude());   // the position is never written down
        boolean moving = location.hasSpeed() && location.getSpeed() * 3.6f > 3f;
        // Head units often have no magnetometer: use the GPS direction of travel instead.
        if (rotation == null && LocationManager.GPS_PROVIDER.equals(location.getProvider()) && location.hasBearing() && moving)
            show(location.getBearing());
        else if (known) show(heading);                                  // the Qibla line follows a new position
        else unknown();
    }

    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values);
        SensorManager.getOrientation(rotMatrix, orientation);
        float az = ((float) Math.toDegrees(orientation[0]) + 360f) % 360f;
        float delta = ((az - heading + 540f) % 360f) - 180f;            // smooth, handling the 0/360 wrap
        show((heading + delta * 0.15f + 360f) % 360f);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
