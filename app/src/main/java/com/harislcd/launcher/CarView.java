package com.harislcd.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The car on the home screen. The picture itself never moves; parts of it react to taps: the wheels spin,
 * the door mirror folds back and out again, a fog lamp starts the indicators, and anywhere else on the
 * car switches the lights. Left alone, the lights fade on and off by themselves.
 */
public class CarView extends View {

    private static final int RAD = 72;                 // rim discs are stored as 144 px circles
    // The folded mirror: this share of the generated swing as tilt, its tip this much smaller, this much darker (of 255).
    private static final float FOLD_LIFT = 0.23f, FOLD_TAPER = 0.86f, FOLD_SHADE = 30f;
    private static final long IDLE_MS = 10000;         // after this long without a tap, things go back to automatic

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final float[] p;
    private Bitmap car, glow, wheelF, wheelR, mirror, mirrorLit, mirrorBg, blink;
    private Bitmap plate;                              // the owner's plate text, drawn flat and then laid on the blank plate
    private String plateText = "";
    private final Matrix plateMatrix = new Matrix();
    private android.graphics.LightingColorFilter foldShade;
    private int foldShadeFor = -1;
    private final Matrix foldMatrix = new Matrix();
    private final float[] foldSrc = new float[8], foldDst = new float[8];

    private float lightAlpha = 0f;                     // 0 = as drawn, 1 = lights fully on
    private boolean lightsManual = false;
    private float wheelAngle = 0f, wheelSpeed = 0f;    // degrees, degrees per second
    private boolean spinning = false;
    private float driveKmh = 0f;                       // the car's real speed; the wheels turn in proportion to it
    // Degrees a second for each km/h. Directly proportional, but geared down from the real thing: a real
    // wheel at 60 km/h turns nine times a second, which a screen can only show as a blur or as going backwards.
    private static final float DEG_PER_KMH = 7f;
    private long lastFrame = 0;
    private float fold = 0f;                           // 0 = mirror out, 1 = folded in

    private final ValueAnimator lightCycle = ValueAnimator.ofFloat(0f, 1f);
    private ValueAnimator lightFade, foldAnim;

    private final Runnable resumeLights = () -> fadeLights(0f, () -> { lightsManual = false; lightCycle.start(); });
    private final Runnable stopWheels = () -> spinning = false;

    // Indicators flash like a real flasher relay: about 85 times a minute, the bulb snapping on and
    // dying away a little more slowly.
    private static final long BLINK_MS = 700, BLINK_ON_MS = 350, BLINK_RISE_MS = 60, BLINK_FALL_MS = 170;
    private boolean blinking = false;
    private long blinkStart = 0;
    private final Runnable blinkFrame = this::invalidate;
    private final Runnable stopBlink = this::stopIndicators;

    public CarView(Context c, AttributeSet a) {
        super(c, a);
        String theme = ThemeStore.theme(c);
        if (android.os.Build.VERSION.SDK_INT >= 26) setDefaultFocusHighlightEnabled(false);   // we draw our own ring
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(ThemeStore.color(c, R.attr.cAccent));
        p = CarParts.of(theme);
        car = load("car_" + theme);
        glow = load("lights_" + theme);
        wheelF = load("wheel_f_" + theme);
        wheelR = load("wheel_r_" + theme);
        mirror = load("mirror_" + theme);
        mirrorLit = load("mirrorlit_" + theme);
        mirrorBg = load("mirrorbg_" + theme);
        blink = load("blink_" + theme);
        setPlate(ThemeStore.plate(c));

        // automatic lights: off, fade on, hold, fade off (9 seconds a round)
        lightCycle.setDuration(9000);
        lightCycle.setInterpolator(new LinearInterpolator());
        lightCycle.setRepeatCount(ValueAnimator.INFINITE);
        lightCycle.addUpdateListener(an -> {
            float t = an.getAnimatedFraction();
            float v = t < 0.30f ? 0f : t < 0.40f ? (t - 0.30f) / 0.10f : t < 0.90f ? 1f : (1f - t) / 0.10f;
            if (v != lightAlpha) { lightAlpha = v; invalidate(); }
        });
    }

    private Bitmap load(String name) {
        int id = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
        if (id == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        return BitmapFactory.decodeResource(getResources(), id, o);
    }

    // ---------- drawing ----------

    @Override
    protected void onDraw(Canvas canvas) {
        if (car == null) return;
        if (isFocused()) {                                   // remote control focus ring
            ring.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
            float in = ring.getStrokeWidth();
            canvas.drawRoundRect(in, in, getWidth() - in, getHeight() - in, 28f, 28f, ring);
        }
        float sx = getWidth() / p[0], sy = getHeight() / p[1];
        dst.set(0, 0, getWidth(), getHeight());
        paint.setAlpha(255);
        canvas.drawBitmap(car, null, dst, paint);
        drawPlate(canvas, sx, sy);

        if (wheelAngle != 0f) {
            drawWheel(canvas, wheelF, p[2], p[3], p[4], p[5], p[6], sx, sy);
            drawWheel(canvas, wheelR, p[7], p[8], p[9], p[10], p[11], sx, sy);
        }
        if (lightAlpha > 0f && glow != null) {
            dst.set(0, 0, getWidth(), getHeight());
            paint.setAlpha(Math.round(255 * lightAlpha));
            canvas.drawBitmap(glow, null, dst, paint);
        }

        float flash = blinkLevel();
        if (flash > 0f && blink != null) {                          // the indicator sections of the headlamps
            dst.set(0, 0, getWidth(), getHeight());
            paint.setAlpha(Math.round(255 * flash));
            canvas.drawBitmap(blink, null, dst, paint);
        }
        // The door mirror. Unfolded and dark it is simply part of the picture; it is drawn again here
        // only while it is folded (swung back against the window) or its indicator strip is lit.
        if ((fold > 0f || flash > 0f) && mirror != null) {
            float l = p[12] * sx, t = p[13] * sy, w = p[14] * sx, h = p[15] * sy;
            float hx = p[16] * sx, hy = p[17] * sy;
            dst.set(l, t, l + w, t + h);
            if (fold > 0f && mirrorBg != null) {
                paint.setAlpha(Math.round(255 * Math.min(1f, fold * 8f)));
                canvas.drawBitmap(mirrorBg, null, dst, paint);      // the door and glass behind it
            }
            canvas.save();
            if (fold > 0f) {
                // A power-fold mirror turns about an upright pin at the hinge until the head lies along the
                // window. Seen from outside the head gets shorter (not thin: squeezed further it turns into a
                // blob), its far end moves away and so looks a little smaller, and it follows the window line.
                float side = hx > l + w / 2f ? -1f : 1f;            // which way the head points from the hinge
                float wide = 1f - (1f - p[19]) * fold;
                double lift = Math.toRadians(-side * p[18] * FOLD_LIFT * fold);
                float cos = (float) Math.cos(lift), sin = (float) Math.sin(lift);
                for (int i = 0; i < 4; i++) {
                    float x = i == 0 || i == 3 ? l : l + w, y = i < 2 ? t : t + h;
                    foldSrc[i * 2] = x;
                    foldSrc[i * 2 + 1] = y;
                    float far = Math.max(0f, side * (x - hx)) / w;  // 0 at the hinge, 1 at the tip
                    float dx = (x - hx) * wide, dy = (y - hy) * (1f - (1f - FOLD_TAPER) * fold * far);
                    foldDst[i * 2] = hx + dx * cos - dy * sin;
                    foldDst[i * 2 + 1] = hy + dx * sin + dy * cos;
                }
                foldMatrix.setPolyToPoly(foldSrc, 0, foldDst, 0, 4);
                canvas.concat(foldMatrix);
                int shade = 255 - Math.round(FOLD_SHADE * fold);    // its face turns away from the light
                if (shade != foldShadeFor) {
                    foldShade = new android.graphics.LightingColorFilter(0xFF000000 | shade << 16 | shade << 8 | shade, 0);
                    foldShadeFor = shade;
                }
                paint.setColorFilter(foldShade);
                paint.setAlpha(255);
                canvas.drawBitmap(mirror, null, dst, paint);
            }
            if (flash > 0f && mirrorLit != null) {
                paint.setAlpha(Math.round(255 * flash));
                canvas.drawBitmap(mirrorLit, null, dst, paint);
            }
            paint.setColorFilter(null);
            canvas.restore();
        }

        if (spinning || driveKmh > 0f || wheelSpeed > 0f) stepWheels();
    }

    /** Picks up the plate text again whenever the screen comes back (it is typed on the Themes page). */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) setPlate(ThemeStore.plate(getContext()));
    }

    void setPlate(String text) {
        text = text == null ? "" : text.trim();
        if (text.equals(plateText)) return;
        plateText = text;
        plate = null;
        if (!text.isEmpty()) {
            int w = 440, h = 210;                           // about the shape of the plate in the pictures
            plate = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(0xF0101010);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
            t.setTextSize(h * 0.62f);
            float tw = t.measureText(text);
            if (tw > w * 0.86f) t.setTextSize(t.getTextSize() * w * 0.86f / tw);     // long plates shrink to fit
            new Canvas(plate).drawText(text, w / 2f, h / 2f - (t.descent() + t.ascent()) / 2f, t);
        }
        invalidate();
    }

    /** Lays the plate text over the blank plate, following the angle the picture was taken from. */
    private void drawPlate(Canvas canvas, float sx, float sy) {
        drawPlateAt(canvas, p, 21 + 2 * (int) p[20], sx, sy, 0f, 0f);
    }

    private void drawPlateAt(Canvas canvas, float[] q, int at, float sx, float sy, float ox, float oy) {
        if (plate == null || q.length < at + 8 || (q[at] == 0f && q[at + 4] == 0f)) return;
        float w = plate.getWidth(), h = plate.getHeight();
        plateMatrix.setPolyToPoly(new float[]{0, 0, w, 0, w, h, 0, h}, 0,
                new float[]{ox + q[at] * sx, oy + q[at + 1] * sy, ox + q[at + 2] * sx, oy + q[at + 3] * sy,
                        ox + q[at + 4] * sx, oy + q[at + 5] * sy, ox + q[at + 6] * sx, oy + q[at + 7] * sy}, 0, 4);
        paint.setAlpha(255);
        canvas.drawBitmap(plate, plateMatrix, paint);
    }


    /** How bright the indicators are right now (0..1), and asks for the next frame when that is about to change. */
    private float blinkLevel() {
        if (!blinking) return 0f;
        long t = (android.os.SystemClock.uptimeMillis() - blinkStart) % BLINK_MS;
        removeCallbacks(blinkFrame);
        if (t < BLINK_RISE_MS) { postInvalidateOnAnimation(); return t / (float) BLINK_RISE_MS; }
        if (t < BLINK_ON_MS) { postDelayed(blinkFrame, BLINK_ON_MS - t); return 1f; }
        if (t < BLINK_ON_MS + BLINK_FALL_MS) {
            postInvalidateOnAnimation();
            float k = 1f - (t - BLINK_ON_MS) / (float) BLINK_FALL_MS;
            return k * k;
        }
        postDelayed(blinkFrame, BLINK_MS - t);
        return 0f;
    }

    /** The rim is stored as a flat circle; squash it back to the wheel's leaning ellipse after rotating it. */
    private void drawWheel(Canvas canvas, Bitmap disc, float cx, float cy, float rx, float ry, float lean, float sx, float sy) {
        if (disc == null) return;
        matrix.reset();
        matrix.postTranslate(-RAD, -RAD);
        matrix.postRotate(wheelAngle);
        matrix.postScale(rx / RAD, ry / RAD);
        matrix.postRotate(lean);
        matrix.postTranslate(cx, cy);
        matrix.postScale(sx, sy);
        paint.setAlpha(255);
        canvas.drawBitmap(disc, matrix, paint);
    }

    private void stepWheels() {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float target = driveKmh > 0f ? driveKmh * DEG_PER_KMH : spinning ? 420f : 0f;   // driving wins over a tap
        wheelSpeed += (target - wheelSpeed) * Math.min(1f, dt * 2.2f);          // ease up and down
        if (target == 0f && wheelSpeed < 4f) { wheelSpeed = 0f; lastFrame = 0; return; }
        wheelAngle = (wheelAngle + wheelSpeed * dt) % 360f;
        if (wheelAngle == 0f) wheelAngle = 0.01f;
        postInvalidateOnAnimation();
    }

    // ---------- taps ----------

    private static boolean inEllipse(float x, float y, float cx, float cy, float rx, float ry) {
        float dx = (x - cx) / rx, dy = (y - cy) / ry;
        return dx * dx + dy * dy <= 1f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() != MotionEvent.ACTION_UP) return super.onTouchEvent(e);
        float x = e.getX() * p[0] / getWidth(), y = e.getY() * p[1] / getHeight();
        // the fog lamps in the bumper corners are the indicator switch
        boolean fog = false;
        for (int i = 0; i < (int) p[20]; i++) fog |= inEllipse(x, y, p[21 + i * 2], p[22 + i * 2], 30f, 30f);
        // the tyre is part of the wheel as far as a finger is concerned, and fingers are not precise
        if (fog) {
            toggleIndicators();
        } else if (inEllipse(x, y, p[2], p[3], p[4] * 1.7f, p[5] * 1.3f) || inEllipse(x, y, p[7], p[8], p[9] * 2.4f, p[10] * 1.3f)) {
            toggleWheels();
        } else if (x > p[12] - p[14] * 0.15f && x < p[12] + p[14] * 1.15f && y > p[13] + p[15] * 0.2f && y < p[13] + p[15] * 1.15f) {
            toggleMirror();
        } else {
            toggleLights();
        }
        performClick();
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }

    /** Tap: lights on. Tap again: off. Ten seconds after the last tap the automatic cycle takes over again. */
    public void toggleLights() {
        lightCycle.cancel();
        removeCallbacks(resumeLights);
        lightsManual = true;
        fadeLights(lightAlpha < 0.5f ? 1f : 0f, null);
        postDelayed(resumeLights, IDLE_MS);
    }

    private void fadeLights(float to, Runnable then) {
        if (lightFade != null) lightFade.cancel();
        lightFade = ValueAnimator.ofFloat(lightAlpha, to);
        lightFade.setDuration(350);
        lightFade.addUpdateListener(an -> { lightAlpha = (float) an.getAnimatedValue(); invalidate(); });
        if (then != null) lightFade.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) { if (!cancelled) then.run(); }
        });
        lightFade.start();
    }

    /** Tap a fog lamp: the indicators blink. Tap again, or wait thirty seconds: they stop. */
    public void toggleIndicators() {
        if (blinking) { stopIndicators(); return; }
        blinking = true;
        blinkStart = android.os.SystemClock.uptimeMillis();
        removeCallbacks(stopBlink);
        invalidate();
        postDelayed(stopBlink, 30000);
    }

    private void stopIndicators() {
        blinking = false;
        removeCallbacks(blinkFrame);
        removeCallbacks(stopBlink);
        invalidate();
    }

    /** Remote control: OK on the car works the lights. */
    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
            toggleLights();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        invalidate();
    }

    /** Tap a wheel: they spin. Tap again, or wait ten seconds: they coast to a stop. */
    /** The car's speed from GPS: the wheels turn in proportion to it, and stand still when it is zero. */
    public void setSpeed(float kmh) {
        kmh = Math.max(0f, kmh);
        if (kmh == driveKmh) return;
        if (driveKmh == 0f && wheelSpeed == 0f) lastFrame = 0;
        driveKmh = kmh;
        invalidate();
    }

    public void toggleWheels() {
        removeCallbacks(stopWheels);
        spinning = !spinning;
        if (spinning) { lastFrame = 0; postDelayed(stopWheels, IDLE_MS); }
        invalidate();
    }

    /** Tap the mirror: it folds in. Tap again: it opens. */
    public void toggleMirror() {
        if (foldAnim != null) foldAnim.cancel();
        foldAnim = ValueAnimator.ofFloat(fold, fold < 0.5f ? 1f : 0f);
        foldAnim.setDuration(1100);                         // a real mirror motor takes about a second
        foldAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        foldAnim.addUpdateListener(an -> { fold = (float) an.getAnimatedValue(); invalidate(); });
        foldAnim.start();
    }

    // ---------- lifecycle ----------

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            if (!lightsManual && !lightCycle.isStarted()) lightCycle.start();
        } else {
            lightCycle.cancel();
            removeCallbacks(resumeLights);
            removeCallbacks(stopWheels);
            stopIndicators();
            spinning = false; wheelSpeed = 0f; lastFrame = 0;
            if (lightsManual) { lightsManual = false; lightAlpha = 0f; }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        lightCycle.cancel();
        if (lightFade != null) lightFade.cancel();
        if (foldAnim != null) foldAnim.cancel();
    }
}
