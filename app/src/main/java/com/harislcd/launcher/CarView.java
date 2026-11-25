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
    private static final long IDLE_MS = 10000;         // after this long without a tap, things go back to automatic

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final float[] p;
    private Bitmap car, glow, wheelF, wheelR, mirror, mirrorLit, mirrorBg, blink;
    private android.graphics.LightingColorFilter foldShade;
    private int foldShadeFor = -1;

    private float lightAlpha = 0f;                     // 0 = as drawn, 1 = lights fully on
    private boolean lightsManual = false;
    private float wheelAngle = 0f, wheelSpeed = 0f;    // degrees, degrees per second
    private boolean spinning = false;
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
                float turn = (hx > l + w / 2f ? 1f : -1f) * p[18] * fold;   // the tip swings up and back
                canvas.rotate(turn, hx, hy);
                canvas.scale(1f - (1f - p[19]) * fold, 1f - 0.06f * fold, hx, hy);
                int shade = 255 - Math.round(40 * fold);            // turned away from the light
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

        if (spinning || wheelSpeed > 0f) stepWheels();
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
        float target = spinning ? 420f : 0f;
        wheelSpeed += (target - wheelSpeed) * Math.min(1f, dt * 2.2f);          // ease up and down
        if (!spinning && wheelSpeed < 4f) { wheelSpeed = 0f; lastFrame = 0; return; }
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
        foldAnim.setDuration(450);
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
