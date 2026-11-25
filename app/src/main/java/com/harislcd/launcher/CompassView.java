package com.harislcd.launcher;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A classic compass, the same in every theme: silver ring, numbered dial (0-315), N/E/S/W with a red N,
 * crosshair lines, and an orange/red needle with a grey tail pointing to the heading (N is up).
 * White face in light mode, dark face in dark mode. Small compasses drop the numbers to stay readable.
 */
public class CompassView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint north = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final boolean dark;
    private final int ringLight, ringDark, ink, soft;
    private float heading = 0f; // degrees, 0 = north
    private int ringFor = -1;

    private static final int ORANGE = 0xFFFF9800, RED = 0xFFE53935, GREY_L = 0xFFB4B4B4, GREY_D = 0xFF7A7A7A;

    public CompassView(Context c, AttributeSet a) {
        super(c, a);
        dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        ringLight = dark ? 0xFF666B72 : 0xFFF7F7F7;
        ringDark = dark ? 0xFF2B2E33 : 0xFFB9B9B9;
        ink = dark ? 0xFFECEFF1 : 0xFF222222;
        soft = dark ? 0xFF8A9096 : 0xFF6E6E6E;
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(dark ? 0xFF111315 : 0xFF9E9E9E);
        face.setColor(dark ? 0xFF1F2226 : 0xFFFFFFFF);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(ink);
        text.setTextAlign(Paint.Align.CENTER);
        north.setColor(RED);
        north.setTextAlign(Paint.Align.CENTER);
        north.setFakeBoldText(true);
    }

    public void setHeading(float deg) {
        heading = (deg % 360f + 360f) % 360f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f - 1f;
        if (r <= 0) return;
        boolean big = r / getResources().getDisplayMetrics().density >= 50f;   // 100dp and up

        // silver ring and face
        if (ringFor != (int) r) {
            ring.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, ringLight, ringDark, Shader.TileMode.CLAMP));
            ringFor = (int) r;
        }
        canvas.drawCircle(cx, cy, r, ring);
        edge.setStrokeWidth(Math.max(1f, r * 0.015f));
        canvas.drawCircle(cx, cy, r, edge);
        float fr = r * 0.92f;
        canvas.drawCircle(cx, cy, fr, face);
        canvas.drawCircle(cx, cy, fr, edge);

        // degree numbers, ticks
        float tOut = big ? 0.72f : 0.86f;
        if (big) {
            text.setTextSize(r * 0.12f);
            text.setFakeBoldText(false);
            float off = (text.descent() + text.ascent()) / 2f;
            for (int d = 0; d < 360; d += 45) {
                double rad = Math.toRadians(d);
                canvas.drawText(String.valueOf(d), cx + (float) Math.sin(rad) * r * 0.81f,
                        cy - (float) Math.cos(rad) * r * 0.81f - off, text);
            }
        }
        int step = big ? 15 : 45;
        for (int d = 0; d < 360; d += step) {
            boolean cardinal = d % 90 == 0, half = d % 45 == 0;
            float len = cardinal ? 0.12f : half ? 0.08f : 0.05f;
            line.setColor(cardinal ? ink : soft);
            line.setStrokeWidth(Math.max(1f, r * (cardinal ? 0.025f : 0.014f)));
            double rad = Math.toRadians(d);
            float s = (float) Math.sin(rad), c = (float) Math.cos(rad);
            canvas.drawLine(cx + s * r * tOut, cy - c * r * tOut,
                    cx + s * r * (tOut - len), cy - c * r * (tOut - len), line);
        }

        // crosshair lines through the centre
        float cr = r * (big ? 0.3f : 0.34f);
        line.setColor(soft);
        line.setStrokeWidth(Math.max(1f, r * 0.01f));
        canvas.drawLine(cx, cy - cr, cx, cy + cr, line);
        canvas.drawLine(cx - cr, cy, cx + cr, cy, line);
        float dg = cr * 0.62f;
        canvas.drawLine(cx - dg, cy - dg, cx + dg, cy + dg, line);
        canvas.drawLine(cx - dg, cy + dg, cx + dg, cy - dg, line);

        // N E S W
        float ts = r * (big ? 0.15f : 0.24f);
        text.setTextSize(ts);
        text.setFakeBoldText(!big);
        north.setTextSize(ts);
        float off = (text.descent() + text.ascent()) / 2f;
        float L = r * (big ? 0.46f : 0.56f);
        canvas.drawText("N", cx, cy - L - off, north);
        canvas.drawText("E", cx + L, cy - off, text);
        canvas.drawText("S", cx, cy + L - off, text);
        canvas.drawText("W", cx - L, cy - off, text);

        // needle: orange / red point, grey tail
        canvas.save();
        canvas.rotate(heading, cx, cy);
        // stays inside the N E S W letters so they are never covered
        float len = r * (big ? 0.36f : 0.4f), w = r * (big ? 0.07f : 0.1f);
        tri(canvas, cx, cy - len, cx - w, cy, cx, cy, ORANGE);
        tri(canvas, cx, cy - len, cx + w, cy, cx, cy, RED);
        tri(canvas, cx, cy + len, cx - w, cy, cx, cy, GREY_L);
        tri(canvas, cx, cy + len, cx + w, cy, cx, cy, GREY_D);
        canvas.restore();
        fill.setColor(dark ? 0xFFECEFF1 : 0xFF333333);
        canvas.drawCircle(cx, cy, r * 0.045f + 1f, fill);
    }

    private void tri(Canvas canvas, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        path.reset();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.close();
        fill.setColor(color);
        canvas.drawPath(path, fill);
    }
}
