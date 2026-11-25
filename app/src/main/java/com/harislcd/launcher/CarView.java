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
 * The car on the home screen. The picture itself never moves; three parts react to taps:
 * the wheels spin, the door mirror folds in and out, and anywhere else on the car switches the lights.
 * Left alone, the lights fade on and off by themselves.
 */
public class CarView extends View {

    private static final int RAD = 72;                 // rim discs are stored as 144 px circles
    private static final long IDLE_MS = 10000;         // after this long without a tap, things go back to automatic

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final float[] p;
    private Bitmap car, glow, wheelF, wheelR, mirror, mirrorBg, blink;

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

    private boolean blinking = false, blinkOn = false;
    private final Runnable blinkTick = new Runnable() {
        @Override public void run() {
            if (!blinking) return;
            blinkOn = !blinkOn;
            invalidate();
            postDelayed(this, 380);
        }
    };
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
            drawWheel(canvas, wheelF, p[2], p[3], p[4], p[5], sx, sy);
            drawWheel(canvas, wheelR, p[6], p[7], p[8], p[9], sx, sy);
        }
        if (fold > 0f && mirror != null && mirrorBg != null) {
            float l = p[10] * sx, t = p[11] * sy, w = p[12] * sx, h = p[13] * sy;
            dst.set(l, t, l + w, t + h);
            paint.setAlpha(Math.round(255 * Math.min(1f, fold * 1.6f)));
            canvas.drawBitmap(mirrorBg, null, dst, paint);          // what is behind the mirror
            paint.setAlpha(255);
            float hingeX = p[14] > 0.5f ? l + w * 0.86f : l + w * 0.14f;
            canvas.save();
            canvas.scale(1f - 0.62f * fold, 1f - 0.10f * fold, hingeX, t + h * 0.55f);
            canvas.drawBitmap(mirror, null, dst, paint);
            canvas.restore();
        }
        if (lightAlpha > 0f && glow != null) {
            dst.set(0, 0, getWidth(), getHeight());
            paint.setAlpha(Math.round(255 * lightAlpha));
            canvas.drawBitmap(glow, null, dst, paint);
        }

        if (blinkOn && blink != null) {
            dst.set(0, 0, getWidth(), getHeight());
            paint.setAlpha(255);
            canvas.drawBitmap(blink, null, dst, paint);
        }

        if (spinning || wheelSpeed > 0f) stepWheels();
    }

    /** The rim is stored as a flat circle; squash it back to the wheel's ellipse after rotating it. */
    private void drawWheel(Canvas canvas, Bitmap disc, float cx, float cy, float rx, float ry, float sx, float sy) {
        if (disc == null) return;
        matrix.reset();
        matrix.postTranslate(-RAD, -RAD);
        matrix.postRotate(wheelAngle);
        matrix.postScale(rx * sx / RAD, ry * sy / RAD);
        matrix.postTranslate(cx * sx, cy * sy);
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
        for (int i = 0; i < (int) p[15]; i++) fog |= inEllipse(x, y, p[16 + i * 2], p[17 + i * 2], 30f, 30f);
        // the tyre is part of the wheel as far as a finger is concerned, and fingers are not precise
        if (fog) {
            toggleIndicators();
        } else if (inEllipse(x, y, p[2], p[3], p[4] * 1.7f, p[5] * 1.45f) || inEllipse(x, y, p[6], p[7], p[8] * 2.2f, p[9] * 1.45f)) {
            toggleWheels();
        } else if (x > p[10] - p[12] * 0.2f && x < p[10] + p[12] * 1.2f && y > p[11] - p[13] * 0.3f && y < p[11] + p[13] * 1.3f) {
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
        blinkOn = false;
        removeCallbacks(blinkTick);
        removeCallbacks(stopBlink);
        post(blinkTick);
        postDelayed(stopBlink, 30000);
    }

    private void stopIndicators() {
        blinking = false;
        blinkOn = false;
        removeCallbacks(blinkTick);
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
