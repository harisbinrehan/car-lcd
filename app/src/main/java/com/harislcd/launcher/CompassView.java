package com.harislcd.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * The compass, the same in every theme and in light and dark: a white rim, a silver band carrying the
 * ticks and the degree numbers (0-315), a white face with N/E/S/W (red N) and crosshair lines, and a long
 * orange/red needle with a grey tail that points where the car is heading (N is up). A small Kaaba sits
 * on the rim in the direction of Mecca, so when the needle points at it the car is facing the Qibla.
 * Small compasses drop the numbers, which would be too small to read, and keep everything else.
 */
public class CompassView extends View {

    private static final double KAABA_LAT = 21.4225, KAABA_LON = 39.8262;
    private static final int ORANGE = 0xFFFF9800, RED = 0xFFE53935, GREY_L = 0xFFB9B9B9, GREY_D = 0xFF7D7D7D;
    private static final int INK = 0xFF1F1F1F, SOFT = 0xFF6E6E6E, GOLD = 0xFFF6B90A;

    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float heading = 0f;      // degrees, 0 = north
    private float qibla = Float.NaN; // bearing of Mecca from here, degrees; NaN until a position is known
    private double qiblaLat = 999, qiblaLon = 999;
    private int shadedFor = -1;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CompassView(Context c, AttributeSet a) {
        super(c, a);
        if (android.os.Build.VERSION.SDK_INT >= 26) setDefaultFocusHighlightEnabled(false);   // we draw our own ring
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(ThemeStore.color(c, R.attr.cAccent));
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0xFF9A9A9A);
        face.setColor(0xFFFFFFFF);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setHeading(float deg) {
        float h = (deg % 360f + 360f) % 360f;
        if (h == heading) return;
        heading = h;
        invalidate();
    }

    /** Where the car is (a rough position is plenty): places the Kaaba marker on the rim. */
    public void setPosition(double lat, double lon) {
        if (Math.abs(lat - qiblaLat) < 0.05 && Math.abs(lon - qiblaLon) < 0.05) return;   // the bearing barely moves
        qiblaLat = lat;
        qiblaLon = lon;
        double p1 = Math.toRadians(lat), p2 = Math.toRadians(KAABA_LAT), dl = Math.toRadians(KAABA_LON - lon);
        double b = Math.atan2(Math.sin(dl), Math.cos(p1) * Math.tan(p2) - Math.sin(p1) * Math.cos(dl));
        qibla = (float) ((Math.toDegrees(b) + 360.0) % 360.0);
        invalidate();
    }

    /** Bearing of Mecca from the last position given, in degrees; NaN while no position is known. */
    public float qibla() { return qibla; }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f - 1f;
        if (r <= 0) return;
        if (isFocused()) {                                   // remote control focus ring, around the dial
            float sw = 3f * getResources().getDisplayMetrics().density;
            ring.setStrokeWidth(sw);
            canvas.drawCircle(cx, cy, r - sw / 2f, ring);
            r -= sw * 1.5f;
        }
        boolean big = r / getResources().getDisplayMetrics().density >= 50f;   // 100dp and up shows the numbers

        // white rim, silver band, white face
        if (shadedFor != (int) r) {
            rim.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFFFFFFF, 0xFFD2D2D2, Shader.TileMode.CLAMP));
            band.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFDADADA, 0xFFF4F4F4, Shader.TileMode.CLAMP));
            shadedFor = (int) r;
        }
        float bandOut = r * (big ? 0.90f : 0.88f), faceR = r * (big ? 0.66f : 0.70f);
        canvas.drawCircle(cx, cy, r, rim);
        edge.setStrokeWidth(Math.max(1f, r * 0.012f));
        canvas.drawCircle(cx, cy, r, edge);
        canvas.drawCircle(cx, cy, bandOut, band);
        canvas.drawCircle(cx, cy, bandOut, edge);
        canvas.drawCircle(cx, cy, faceR, face);

        // ticks on the band; on a big compass the numbers too, turned to follow the ring
        int step = big ? 15 : 45;
        for (int d = 0; d < 360; d += step) {
            boolean cardinal = d % 90 == 0, half = d % 45 == 0;
            if (big && half) continue;                                 // a number stands here instead of a long tick
            float out = big ? 0.86f : 0.84f, len = big ? 0.08f : (cardinal ? 0.12f : 0.08f);
            line.setColor(cardinal ? INK : SOFT);
            line.setStrokeWidth(Math.max(1f, r * (cardinal ? 0.03f : 0.016f)));
            double rad = Math.toRadians(d);
            float s = (float) Math.sin(rad), c = (float) Math.cos(rad);
            canvas.drawLine(cx + s * r * out, cy - c * r * out, cx + s * r * (out - len), cy - c * r * (out - len), line);
        }
        if (big) {
            text.setColor(INK);
            text.setFakeBoldText(false);
            text.setTextSize(r * 0.125f);
            float numOff = (text.descent() + text.ascent()) / 2f;
            for (int d = 0; d < 360; d += 45) {
                canvas.save();
                canvas.rotate(d, cx, cy);
                canvas.drawText(String.valueOf(d), cx, cy - r * 0.78f - numOff, text);
                line.setColor(INK);
                line.setStrokeWidth(Math.max(1f, r * 0.02f));
                canvas.drawLine(cx, cy - r * 0.71f, cx, cy - r * 0.665f, line);
                canvas.restore();
            }
        }

        // crosshair lines through the centre
        float cr = faceR * (big ? 0.62f : 0.5f);
        line.setColor(SOFT);
        line.setStrokeWidth(Math.max(1f, r * 0.01f));
        canvas.drawLine(cx, cy - cr, cx, cy + cr, line);
        canvas.drawLine(cx - cr, cy, cx + cr, cy, line);
        float dg = cr * 0.6f;
        canvas.drawLine(cx - dg, cy - dg, cx + dg, cy + dg, line);
        canvas.drawLine(cx - dg, cy + dg, cx + dg, cy - dg, line);

        // needle: orange / red point, grey tail
        canvas.save();
        canvas.rotate(heading, cx, cy);
        float len = r * (big ? 0.74f : 0.72f), w = r * (big ? 0.055f : 0.085f);
        tri(cx, cy - len, cx - w, cy, cx, cy, ORANGE, canvas);
        tri(cx, cy - len, cx + w, cy, cx, cy, RED, canvas);
        tri(cx, cy + len, cx - w, cy, cx, cy, GREY_L, canvas);
        tri(cx, cy + len, cx + w, cy, cx, cy, GREY_D, canvas);
        canvas.restore();
        fill.setColor(0xFF333333);
        canvas.drawCircle(cx, cy, r * 0.04f + 1f, fill);

        // N E S W, on top of the needle with a white edge so a letter stays readable when the needle points at it
        text.setTextSize(r * (big ? 0.15f : 0.26f));
        float off = (text.descent() + text.ascent()) / 2f;
        float L = faceR * (big ? 0.80f : 0.66f);
        for (int pass = 0; pass < 2; pass++) {
            text.setStyle(pass == 0 ? Paint.Style.STROKE : Paint.Style.FILL);
            text.setStrokeWidth(r * 0.07f);
            text.setFakeBoldText(true);
            text.setColor(pass == 0 ? 0xFFFFFFFF : RED);
            canvas.drawText("N", cx, cy - L - off, text);
            text.setFakeBoldText(!big);
            text.setColor(pass == 0 ? 0xFFFFFFFF : INK);
            canvas.drawText("E", cx + L, cy - off, text);
            canvas.drawText("S", cx, cy + L - off, text);
            canvas.drawText("W", cx - L, cy - off, text);
        }
        text.setStyle(Paint.Style.FILL);

        // the Kaaba on the rim, towards Mecca
        if (!Float.isNaN(qibla)) {
            double rad = Math.toRadians(qibla);
            float s = r * (big ? 0.30f : 0.36f), at = r - s * 0.54f;
            kaaba(canvas, cx + (float) Math.sin(rad) * at, cy - (float) Math.cos(rad) * at, s);
        }
    }

    /** A little cube seen from above one corner: white roof, black walls, the gold band and door. */
    private void kaaba(Canvas canvas, float x, float y, float s) {
        float hw = s * 0.5f, top = y - s * 0.60f, mid = y - s * 0.34f, low = y - s * 0.08f, base = y + s * 0.52f, side = y + s * 0.26f;
        // a pale outline keeps it clear of the dark needle and of dark screens behind the rim
        fill.setColor(0xFFFFFFFF);
        path.reset();
        path.moveTo(x, top - s * 0.06f);
        path.lineTo(x + hw + s * 0.06f, mid - s * 0.02f);
        path.lineTo(x + hw + s * 0.06f, side + s * 0.03f);
        path.lineTo(x, base + s * 0.06f);
        path.lineTo(x - hw - s * 0.06f, side + s * 0.03f);
        path.lineTo(x - hw - s * 0.06f, mid - s * 0.02f);
        path.close();
        canvas.drawPath(path, fill);
        quad(canvas, x - hw, mid, x, low, x, base, x - hw, side, 0xFF151515);           // left wall
        quad(canvas, x, low, x + hw, mid, x + hw, side, x, base, 0xFF242424);           // right wall
        float b0 = 0.16f, b1 = 0.36f;                                                    // the band, as a share of the wall's height
        quad(canvas, x - hw, mid + (side - mid) * b0, x, low + (base - low) * b0, x, low + (base - low) * b1, x - hw, mid + (side - mid) * b1, GOLD);
        quad(canvas, x, low + (base - low) * b0, x + hw, mid + (side - mid) * b0, x + hw, mid + (side - mid) * b1, x, low + (base - low) * b1, GOLD);
        float dx0 = x + hw * 0.38f, dx1 = x + hw * 0.66f;                                // the door, on the right wall
        float slope = (mid - low) / hw;
        quad(canvas, dx0, low + (dx0 - x) * slope + (base - low) * 0.50f, dx1, low + (dx1 - x) * slope + (base - low) * 0.50f,
                dx1, low + (dx1 - x) * slope + (base - low) * 0.93f, dx0, low + (dx0 - x) * slope + (base - low) * 0.93f, GOLD);
        quad(canvas, x, top, x + hw, mid, x, low, x - hw, mid, 0xFFFFFFFF);              // roof
        line.setColor(0xFF151515);
        line.setStrokeWidth(Math.max(1f, s * 0.045f));
        canvas.drawLine(x, top, x + hw, mid, line);
        canvas.drawLine(x + hw, mid, x, low, line);
        canvas.drawLine(x, low, x - hw, mid, line);
        canvas.drawLine(x - hw, mid, x, top, line);
    }

    private void quad(Canvas canvas, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {
        path.reset();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x4, y4);
        path.close();
        fill.setColor(color);
        canvas.drawPath(path, fill);
    }

    private void tri(float x1, float y1, float x2, float y2, float x3, float y3, int color, Canvas canvas) {
        path.reset();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.close();
        fill.setColor(color);
        canvas.drawPath(path, fill);
    }
}
