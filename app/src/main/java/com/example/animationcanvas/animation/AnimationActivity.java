package com.example.animationcanvas.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.animationcanvas.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình minh họa Property Animation:
 * - Nhân vật trên sân khấu (MascotCharacterView) được vẽ bằng Canvas, tự động chớp mắt,
 *   liếc mắt theo hướng di chuyển và có vành đai xoay quanh.
 * - Minh họa 8 kiểu chuyển động: Move, Rotate, Scale, Fade, Bounce (Squash & Stretch),
 *   Orbit, Wave và Combo (AnimatorSet).
 * - Đồ thị Interpolator hiển thị đường cong gia tốc theo thời gian thực.
 */
public class AnimationActivity extends AppCompatActivity {

    private static final long MIN_DURATION = 250;

    private static final String[] INTERPOLATOR_NAMES = {
            "AccelerateDecelerate", "Linear", "Accelerate", "Decelerate",
            "Overshoot", "AnticipateOvershoot", "Bounce"
    };

    private FrameLayout stage;
    private FrameLayout target;
    private View shadow;
    private TextView tvCode;
    private TextView tvDuration;
    private MaterialButton btnSwitchShape;

    private MascotCharacterView mascotView;
    private StageBackdropView stageBackdrop;
    private InterpolatorGraphView interpGraph;

    private Interpolator interpolator = new AccelerateDecelerateInterpolator();
    private String interpolatorName = INTERPOLATOR_NAMES[0];
    private long duration = 900;
    private boolean infiniteLoop = false;
    private int shapeIndex = 0;

    private Animator currentAnimator;
    private ValueAnimator progressTicker;
    private float prevTx = 0f, prevTy = 0f;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_animation);

        stage = findViewById(R.id.stage);
        target = findViewById(R.id.target);
        shadow = findViewById(R.id.shadow);
        tvCode = findViewById(R.id.tvCode);
        tvDuration = findViewById(R.id.tvDuration);
        btnSwitchShape = findViewById(R.id.btnSwitchShape);

        target.setCameraDistance(8000f * getResources().getDisplayMetrics().density);

        // Gắn nhân vật động vào trong target
        mascotView = new MascotCharacterView(this);
        target.addView(mascotView);

        // Gắn nền sân khấu và đồ thị Interpolator
        FrameLayout stageHolder = findViewById(R.id.stageCanvasHolder);
        stageBackdrop = new StageBackdropView(this);
        stageHolder.addView(stageBackdrop);

        FrameLayout graphHolder = findViewById(R.id.interpGraphHolder);
        interpGraph = new InterpolatorGraphView(this);
        interpGraph.setInterpolator(interpolator);
        graphHolder.addView(interpGraph);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSwitchShape.setOnClickListener(v -> cycleTargetShape());

        setupInterpolatorChips();
        setupDurationAndLoop();

        findViewById(R.id.btnMove).setOnClickListener(v -> playMove());
        findViewById(R.id.btnRotate).setOnClickListener(v -> playRotate());
        findViewById(R.id.btnScale).setOnClickListener(v -> playScale());
        findViewById(R.id.btnFade).setOnClickListener(v -> playFade());
        findViewById(R.id.btnBounce).setOnClickListener(v -> playBounce());
        findViewById(R.id.btnOrbit).setOnClickListener(v -> playOrbit());
        findViewById(R.id.btnShake).setOnClickListener(v -> playWave());
        findViewById(R.id.btnCombo).setOnClickListener(v -> playCombo());
        findViewById(R.id.btnReset).setOnClickListener(v -> resetTarget(true));

        target.setOnClickListener(v -> v.animate()
                .rotationBy(360f)
                .scaleX(1.18f).scaleY(1.18f)
                .setDuration(420)
                .setInterpolator(new OvershootInterpolator(2.4f))
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(200).start())
                .start());

        stage.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                float cx = stage.getWidth() / 2f;
                float cy = stage.getHeight() / 2f;
                float tx = event.getX() - cx;
                float ty = event.getY() - cy;
                float limitX = maxHorizontalDistance();
                float limitY = maxVerticalDistance();
                tx = Math.max(-limitX, Math.min(limitX, tx));
                ty = Math.max(-limitY, Math.min(limitY, ty));
                stageBackdrop.spawnTargetMarker(cx + tx, cy + ty);
                playTouchMove(tx, ty);
                return true;
            }
            return false;
        });
    }

    private void cycleTargetShape() {
        shapeIndex = (shapeIndex + 1) % 4;
        String[] labels = {"Nhân vật: Bóng", "Nhân vật: Sao", "Nhân vật: Robot", "Nhân vật: Đĩa bay"};
        btnSwitchShape.setText(labels[shapeIndex]);
        mascotView.setCharacterType(shapeIndex);

        target.animate().scaleX(0.75f).scaleY(0.75f).setDuration(90)
                .withEndAction(() -> target.animate().scaleX(1f).scaleY(1f).setDuration(280)
                        .setInterpolator(new OvershootInterpolator(3f)).start())
                .start();
    }

    private void setupInterpolatorChips() {
        ChipGroup group = findViewById(R.id.chipGroupInterpolator);
        ColorStateList bg = ContextCompat.getColorStateList(this, R.color.tool_bg);
        ColorStateList text = ContextCompat.getColorStateList(this, R.color.tool_text);

        for (int i = 0; i < INTERPOLATOR_NAMES.length; i++) {
            final int index = i;
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setText(INTERPOLATOR_NAMES[i]);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColor(bg);
            chip.setTextColor(text);
            chip.setChipStrokeWidth(0f);
            chip.setOnClickListener(v -> {
                interpolator = createInterpolator(index);
                interpolatorName = INTERPOLATOR_NAMES[index];
                interpGraph.setInterpolator(interpolator);
                playMove();
            });
            group.addView(chip);
            if (i == 0) chip.setChecked(true);
        }
    }

    private Interpolator createInterpolator(int index) {
        switch (index) {
            case 1: return new LinearInterpolator();
            case 2: return new AccelerateInterpolator(1.8f);
            case 3: return new DecelerateInterpolator(1.8f);
            case 4: return new OvershootInterpolator(2.2f);
            case 5: return new AnticipateOvershootInterpolator(2.0f);
            case 6: return new BounceInterpolator();
            default: return new AccelerateDecelerateInterpolator();
        }
    }

    private void setupDurationAndLoop() {
        SeekBar seek = findViewById(R.id.seekDuration);
        seek.setProgress((int) (duration - MIN_DURATION));
        updateDurationLabel();
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                duration = MIN_DURATION + progress;
                updateDurationLabel();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        MaterialSwitch switchLoop = findViewById(R.id.switchLoop);
        switchLoop.setOnCheckedChangeListener((buttonView, isChecked) -> infiniteLoop = isChecked);
    }

    private void updateDurationLabel() {
        tvDuration.setText(String.format(Locale.US, "%d ms", duration));
    }

    // ------------------------------------------------------------------
    // Các chế độ Animation
    // ------------------------------------------------------------------

    private void playMove() {
        float d = maxHorizontalDistance();
        ObjectAnimator move = ObjectAnimator.ofFloat(target, View.TRANSLATION_X, 0f, d, -d, 0f);
        ObjectAnimator moveShadow = ObjectAnimator.ofFloat(shadow, View.TRANSLATION_X, 0f, d, -d, 0f);
        applyRepeat(move);
        applyRepeat(moveShadow);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(move, moveShadow);
        set.setDuration(duration * 2);
        set.setInterpolator(interpolator);
        start(set, duration * 2,
                "ObjectAnimator.ofFloat(view, \"translationX\", 0f, " + fmt(d) + ", " + fmt(-d) + ", 0f)");
    }

    private void playTouchMove(float targetX, float targetY) {
        ObjectAnimator mx = ObjectAnimator.ofFloat(target, View.TRANSLATION_X, target.getTranslationX(), targetX);
        ObjectAnimator my = ObjectAnimator.ofFloat(target, View.TRANSLATION_Y, target.getTranslationY(), targetY);
        ObjectAnimator sx = ObjectAnimator.ofFloat(shadow, View.TRANSLATION_X, shadow.getTranslationX(), targetX);
        ObjectAnimator sy = ObjectAnimator.ofFloat(shadow, View.TRANSLATION_Y, shadow.getTranslationY(), targetY);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(mx, my, sx, sy);
        set.setDuration(duration);
        set.setInterpolator(interpolator);
        startWithoutReset(set, duration,
                "ObjectAnimator.ofFloat(view, \"translationX/Y\", " + fmt(targetX) + ", " + fmt(targetY) + ")");
    }

    private void playRotate() {
        PropertyValuesHolder rotZ = PropertyValuesHolder.ofFloat(View.ROTATION, 0f, 360f);
        PropertyValuesHolder rotY = PropertyValuesHolder.ofFloat(View.ROTATION_Y, 0f, 360f);
        ObjectAnimator rotate = ObjectAnimator.ofPropertyValuesHolder(target, rotZ, rotY);
        rotate.setDuration(duration);
        rotate.setInterpolator(interpolator);
        applyRepeat(rotate);
        start(rotate, duration,
                "ObjectAnimator.ofFloat(view, \"rotation\", 0f, 360f)");
    }

    private void playScale() {
        PropertyValuesHolder sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.8f, 0.8f, 1f);
        PropertyValuesHolder sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.8f, 0.8f, 1f);
        ObjectAnimator scale = ObjectAnimator.ofPropertyValuesHolder(target, sx, sy);
        scale.setDuration(duration);
        scale.setInterpolator(interpolator);
        applyRepeat(scale);

        ObjectAnimator shadowScale = ObjectAnimator.ofFloat(shadow, View.SCALE_X, 1f, 1.55f, 0.75f, 1f);
        shadowScale.setDuration(duration);
        shadowScale.setInterpolator(interpolator);
        applyRepeat(shadowScale);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scale, shadowScale);
        start(set, duration,
                "PropertyValuesHolder.ofFloat(\"scaleX/Y\", 1f, 1.8f, 1f)");
    }

    private void playFade() {
        ObjectAnimator fade = ObjectAnimator.ofFloat(target, View.ALPHA, 1f, 0.08f, 1f);
        fade.setDuration(duration * 2);
        fade.setInterpolator(interpolator);
        applyRepeat(fade);

        ObjectAnimator shadowFade = ObjectAnimator.ofFloat(shadow, View.ALPHA, 0.38f, 0.05f, 0.38f);
        shadowFade.setDuration(duration * 2);
        shadowFade.setInterpolator(interpolator);
        applyRepeat(shadowFade);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(fade, shadowFade);
        start(set, duration * 2,
                "ObjectAnimator.ofFloat(view, \"alpha\", 1f, 0.08f, 1f)");
    }

    private void playBounce() {
        float top = -maxVerticalDistance();
        long bounceDur = Math.max(duration, 1000);
        Interpolator bounceInterp = new BounceInterpolator();

        ObjectAnimator fall = ObjectAnimator.ofFloat(target, View.TRANSLATION_Y, top, 0f);
        fall.setInterpolator(bounceInterp);
        fall.setDuration(bounceDur);
        applyRepeat(fall);

        PropertyValuesHolder squashX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.88f, 1.22f, 0.94f, 1.06f, 1f);
        PropertyValuesHolder squashY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.15f, 0.78f, 1.06f, 0.95f, 1f);
        ObjectAnimator squash = ObjectAnimator.ofPropertyValuesHolder(target, squashX, squashY);
        squash.setDuration(bounceDur);
        applyRepeat(squash);

        PropertyValuesHolder shadowScale = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.25f, 1.25f);
        PropertyValuesHolder shadowAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.08f, 0.45f);
        ObjectAnimator shadowAnim = ObjectAnimator.ofPropertyValuesHolder(shadow, shadowScale, shadowAlpha);
        shadowAnim.setInterpolator(bounceInterp);
        shadowAnim.setDuration(bounceDur);
        applyRepeat(shadowAnim);

        interpGraph.setInterpolator(bounceInterp);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(fall, squash, shadowAnim);
        start(set, bounceDur,
                "ObjectAnimator.ofFloat(view, \"translationY\", " + fmt(top) + ", 0f)\n  .setInterpolator(new BounceInterpolator())",
                "Bounce");
    }

    private void playOrbit() {
        final float rx = maxHorizontalDistance() * 0.70f;
        final float ry = maxVerticalDistance() * 0.65f;
        ValueAnimator orbit = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        orbit.setDuration(duration * 2);
        orbit.setInterpolator(interpolator);
        if (infiniteLoop) {
            orbit.setRepeatCount(ValueAnimator.INFINITE);
            orbit.setRepeatMode(ValueAnimator.RESTART);
        }
        orbit.addUpdateListener(anim -> {
            float angle = (float) anim.getAnimatedValue();
            float x = (float) Math.sin(angle) * rx;
            float y = (float) (Math.cos(angle) - 1f) * 0.5f * ry;
            target.setTranslationX(x);
            target.setTranslationY(y);
            shadow.setTranslationX(x);
            shadow.setScaleX(0.8f + 0.3f * (float) Math.cos(angle));
        });
        start(orbit, duration * 2,
                "ValueAnimator.ofFloat(0f, 2*PI) // Quỹ đạo tròn (sin, cos)");
    }

    private void playWave() {
        float d = maxHorizontalDistance() * 0.75f;
        float h = maxVerticalDistance() * 0.55f;
        PropertyValuesHolder tx = PropertyValuesHolder.ofFloat(View.TRANSLATION_X,
                0f, d * 0.5f, d, d * 0.5f, 0f, -d * 0.5f, -d, -d * 0.5f, 0f);
        PropertyValuesHolder ty = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y,
                0f, -h, 0f, h, 0f, -h, 0f, h, 0f);
        PropertyValuesHolder rot = PropertyValuesHolder.ofFloat(View.ROTATION,
                0f, 20f, -20f, 15f, -15f, 10f, -10f, 0f);
        ObjectAnimator wave = ObjectAnimator.ofPropertyValuesHolder(target, tx, ty, rot);
        wave.setDuration(duration * 2);
        wave.setInterpolator(interpolator);
        applyRepeat(wave);

        ObjectAnimator sx = ObjectAnimator.ofFloat(shadow, View.TRANSLATION_X,
                0f, d * 0.5f, d, d * 0.5f, 0f, -d * 0.5f, -d, -d * 0.5f, 0f);
        sx.setDuration(duration * 2);
        sx.setInterpolator(interpolator);
        applyRepeat(sx);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(wave, sx);
        start(set, duration * 2,
                "ObjectAnimator.ofPropertyValuesHolder(view, tx, ty, rotation)");
    }

    private void playCombo() {
        float d = maxHorizontalDistance() * 0.78f;
        long step = Math.max(duration / 2, 180);

        AnimatorSet grow = new AnimatorSet();
        grow.playTogether(
                ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, 1.4f),
                ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, 1.4f),
                ObjectAnimator.ofFloat(target, View.ROTATION, 0f, 180f));
        grow.setDuration(step);

        AnimatorSet moveRight = new AnimatorSet();
        moveRight.playTogether(
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X, 0f, d),
                ObjectAnimator.ofFloat(shadow, View.TRANSLATION_X, 0f, d));
        moveRight.setDuration(step);

        AnimatorSet moveLeft = new AnimatorSet();
        moveLeft.playTogether(
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X, d, -d),
                ObjectAnimator.ofFloat(shadow, View.TRANSLATION_X, d, -d));
        moveLeft.setDuration(step * 2);

        AnimatorSet back = new AnimatorSet();
        back.playTogether(
                ObjectAnimator.ofFloat(target, View.TRANSLATION_X, -d, 0f),
                ObjectAnimator.ofFloat(shadow, View.TRANSLATION_X, -d, 0f),
                ObjectAnimator.ofFloat(target, View.SCALE_X, 1.4f, 1f),
                ObjectAnimator.ofFloat(target, View.SCALE_Y, 1.4f, 1f),
                ObjectAnimator.ofFloat(target, View.ROTATION, 180f, 360f),
                ObjectAnimator.ofFloat(target, View.ALPHA, 1f, 0.3f, 1f));
        back.setDuration(step);

        AnimatorSet combo = new AnimatorSet();
        combo.playSequentially(grow, moveRight, moveLeft, back);
        combo.setInterpolator(interpolator);
        start(combo, step * 5,
                "AnimatorSet: playSequentially(grow, moveRight, moveLeft, back)");
    }

    private void applyRepeat(ValueAnimator animator) {
        if (infiniteLoop) {
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
        }
    }

    // ------------------------------------------------------------------
    // Điều phối
    // ------------------------------------------------------------------

    private void start(Animator animator, long totalDuration, String code) {
        start(animator, totalDuration, code, interpolatorName);
    }

    private void start(Animator animator, long totalDuration, String code, String interpLabel) {
        resetTarget(false);
        launchWithTicker(animator, totalDuration, code, interpLabel);
    }

    private void startWithoutReset(Animator animator, long totalDuration, String code) {
        if (currentAnimator != null) currentAnimator.cancel();
        if (progressTicker != null) progressTicker.cancel();
        launchWithTicker(animator, totalDuration, code, interpolatorName);
    }

    private void launchWithTicker(Animator animator, long totalDuration, String code, String interpLabel) {
        tvCode.setText(String.format(Locale.US, "%s\n// %d ms · %s", code, totalDuration, interpLabel));

        progressTicker = ValueAnimator.ofFloat(0f, 1f);
        progressTicker.setDuration(totalDuration);
        progressTicker.setInterpolator(new LinearInterpolator());
        if (infiniteLoop) {
            progressTicker.setRepeatCount(ValueAnimator.INFINITE);
            progressTicker.setRepeatMode(ValueAnimator.REVERSE);
        }
        progressTicker.addUpdateListener(anim -> {
            float fraction = (float) anim.getAnimatedValue();
            interpGraph.setProgress(fraction);

            float curTx = target.getTranslationX();
            float curTy = target.getTranslationY();
            mascotView.setLookDirection(curTx - prevTx, curTy - prevTy);
            prevTx = curTx;
            prevTy = curTy;

            float cx = stage.getWidth() / 2f + curTx;
            float cy = stage.getHeight() / 2f + curTy;
            stageBackdrop.recordTrailPoint(cx, cy);
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentAnimator == animation) {
                    currentAnimator = null;
                    mascotView.setLookDirection(0f, 0f);
                }
            }
        });

        currentAnimator = animator;
        animator.start();
        progressTicker.start();
    }

    private void resetTarget(boolean animated) {
        if (currentAnimator != null) {
            currentAnimator.cancel();
            currentAnimator = null;
        }
        if (progressTicker != null) {
            progressTicker.cancel();
            progressTicker = null;
        }
        target.animate().cancel();
        stageBackdrop.clearTrail();
        interpGraph.setProgress(0f);
        mascotView.setLookDirection(0f, 0f);

        if (animated) {
            target.animate().translationX(0f).translationY(0f)
                    .rotation(0f).rotationX(0f).rotationY(0f)
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(300).setInterpolator(new OvershootInterpolator(1.4f)).start();
            shadow.animate().translationX(0f).translationY(0f).scaleX(1f).alpha(0.38f).setDuration(300).start();
            tvCode.setText(R.string.anim_hint);
        } else {
            target.setTranslationX(0f);
            target.setTranslationY(0f);
            target.setRotation(0f);
            target.setRotationX(0f);
            target.setRotationY(0f);
            target.setScaleX(1f);
            target.setScaleY(1f);
            target.setAlpha(1f);
            shadow.setTranslationX(0f);
            shadow.setTranslationY(0f);
            shadow.setScaleX(1f);
            shadow.setAlpha(0.38f);
        }
    }

    private float maxHorizontalDistance() {
        return Math.max(0f, (stage.getWidth() - target.getWidth()) / 2f - dp(16));
    }

    private float maxVerticalDistance() {
        return Math.max(0f, (stage.getHeight() - target.getHeight()) / 2f - dp(24));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static String fmt(float v) {
        return String.format(Locale.US, "%.0ff", v);
    }

    @Override
    protected void onDestroy() {
        if (currentAnimator != null) currentAnimator.cancel();
        if (progressTicker != null) progressTicker.cancel();
        super.onDestroy();
    }

    // =========================================================================
    // Nhân vật Mascot vẽ bằng Canvas: tự động chớp mắt & liếc mắt theo vận tốc
    // =========================================================================
    private static class MascotCharacterView extends View {
        private final float density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path starPath = new Path();
        private int charType = 0;
        private float lookX = 0f, lookY = 0f;
        private float time = 0f;
        private long lastNanos = 0L;

        MascotCharacterView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
        }

        void setCharacterType(int type) {
            this.charType = type;
            invalidate();
        }

        void setLookDirection(float vx, float vy) {
            float maxOffset = 4.5f * density;
            lookX = Math.max(-maxOffset, Math.min(maxOffset, vx * 0.45f));
            lookY = Math.max(-maxOffset, Math.min(maxOffset, vy * 0.45f));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            long now = System.nanoTime();
            float dt = lastNanos == 0L ? 1f / 60f : (now - lastNanos) / 1_000_000_000f;
            lastNanos = now;
            if (dt > 0.05f) dt = 0.05f;
            time += dt;

            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) * 0.42f;

            // Vòng vệ tinh nhỏ bay quanh nhân vật
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f * density);
            paint.setColor(Color.parseColor("#55FFFFFF"));
            canvas.drawCircle(cx, cy, r * 1.12f, paint);

            float orbAngle = time * 2.5f;
            float ox = cx + (float) Math.cos(orbAngle) * r * 1.12f;
            float oy = cy + (float) Math.sin(orbAngle) * r * 1.12f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#FDE047"));
            canvas.drawCircle(ox, oy, 3.5f * density, paint);

            // Thân nhân vật theo 4 kiểu
            if (charType == 0) {
                // Kiểu 0: Bóng Slime tròn trịa
                paint.setColor(Color.parseColor("#6366F1"));
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(Color.parseColor("#818CF8"));
                canvas.drawCircle(cx - r * 0.25f, cy - r * 0.28f, r * 0.25f, paint);
            } else if (charType == 1) {
                // Kiểu 1: Ngôi sao vàng
                paint.setColor(Color.parseColor("#F59E0B"));
                buildStar(starPath, cx, cy, r * 1.08f);
                canvas.drawPath(starPath, paint);
            } else if (charType == 2) {
                // Kiểu 2: Robot vuông bo góc có ăng-ten
                paint.setColor(Color.parseColor("#10B981"));
                canvas.drawLine(cx, cy - r * 1.15f, cx, cy - r * 0.8f, paint);
                canvas.drawCircle(cx, cy - r * 1.15f, 3.5f * density, paint);
                canvas.drawRoundRect(cx - r * 0.88f, cy - r * 0.82f,
                        cx + r * 0.88f, cy + r * 0.88f,
                        r * 0.35f, r * 0.35f, paint);
            } else {
                // Kiểu 3: Đĩa bay UFO
                paint.setColor(Color.parseColor("#38BDF8"));
                canvas.drawOval(cx - r * 0.65f, cy - r * 0.78f, cx + r * 0.65f, cy + r * 0.1f, paint);
                paint.setColor(Color.parseColor("#F43F5E"));
                canvas.drawOval(cx - r * 1.08f, cy - r * 0.22f, cx + r * 1.08f, cy + r * 0.55f, paint);
            }

            // Đôi mắt chớp tự nhiên mỗi 3.2 giây
            boolean blinking = (time % 3.2f) > 3.05f;
            float eyeSpacing = r * 0.34f;
            float eyeY = cy - r * 0.06f + lookY * 0.5f;

            if (blinking) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f * density);
                paint.setColor(Color.WHITE);
                canvas.drawLine(cx - eyeSpacing - 4f * density, eyeY, cx - eyeSpacing + 4f * density, eyeY, paint);
                canvas.drawLine(cx + eyeSpacing - 4f * density, eyeY, cx + eyeSpacing + 4f * density, eyeY, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                float eyeR = r * 0.22f;
                canvas.drawCircle(cx - eyeSpacing, eyeY, eyeR, paint);
                canvas.drawCircle(cx + eyeSpacing, eyeY, eyeR, paint);

                // Con ngươi liếc theo hướng chạy
                paint.setColor(Color.parseColor("#0F172A"));
                float pupilR = eyeR * 0.52f;
                canvas.drawCircle(cx - eyeSpacing + lookX, eyeY + lookY, pupilR, paint);
                canvas.drawCircle(cx + eyeSpacing + lookX, eyeY + lookY, pupilR, paint);
            }

            postInvalidateOnAnimation();
        }

        private void buildStar(Path path, float cx, float cy, float outerR) {
            path.reset();
            float innerR = outerR * 0.52f;
            for (int i = 0; i < 10; i++) {
                float rad = (i % 2 == 0) ? outerR : innerR;
                double a = -Math.PI / 2 + i * Math.PI / 5;
                float px = cx + (float) (Math.cos(a) * rad);
                float py = cy + (float) (Math.sin(a) * rad);
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            path.close();
        }
    }

    // =========================================================================
    // Nền sân khấu: Lưới nhẹ + Vệt chuyển động mượt
    // =========================================================================
    private static class StageBackdropView extends View {
        private static final int MAX_TRAIL = 32;

        private final float density;
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path trailPath = new Path();

        private final List<float[]> trail = new ArrayList<>();
        private float markerX = -1f, markerY = -1f, markerAlpha = 0f, markerRadius = 0f;

        StageBackdropView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;

            gridPaint.setColor(Color.parseColor("#12FFFFFF"));
            gridPaint.setStrokeWidth(1f);

            trailPaint.setStyle(Paint.Style.STROKE);
            trailPaint.setStrokeCap(Paint.Cap.ROUND);
            trailPaint.setStrokeJoin(Paint.Join.ROUND);
            trailPaint.setStrokeWidth(2.5f * density);
            trailPaint.setColor(Color.parseColor("#66818CF8"));

            markerPaint.setStyle(Paint.Style.STROKE);
            markerPaint.setStrokeWidth(2f * density);
            markerPaint.setColor(Color.parseColor("#38BDF8"));
        }

        void recordTrailPoint(float x, float y) {
            if (trail.size() >= MAX_TRAIL) trail.remove(0);
            trail.add(new float[]{x, y});
            invalidate();
        }

        void clearTrail() {
            trail.clear();
            invalidate();
        }

        void spawnTargetMarker(float x, float y) {
            markerX = x;
            markerY = y;
            markerRadius = 8f * density;
            markerAlpha = 1f;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float step = 30f * density;

            for (float x = step; x < w; x += step) {
                for (float y = step; y < h; y += step) {
                    canvas.drawCircle(x, y, 1.2f * density, gridPaint);
                }
            }

            int n = trail.size();
            if (n > 1) {
                trailPath.reset();
                trailPath.moveTo(trail.get(0)[0], trail.get(0)[1]);
                for (int i = 1; i < n; i++) {
                    float[] pt = trail.get(i);
                    trailPath.lineTo(pt[0], pt[1]);
                }
                canvas.drawPath(trailPath, trailPaint);

                for (int i = 0; i < n; i += 3) {
                    float ratio = (i + 1f) / n;
                    float[] pt = trail.get(i);
                    dotPaint.setColor(Color.parseColor("#818CF8"));
                    dotPaint.setAlpha((int) (ratio * 130));
                    canvas.drawCircle(pt[0], pt[1], (2f + 3.5f * ratio) * density, dotPaint);
                }
            }

            if (markerAlpha > 0.02f) {
                markerPaint.setAlpha((int) (markerAlpha * 200));
                canvas.drawCircle(markerX, markerY, markerRadius, markerPaint);
                markerRadius += 2.2f * density;
                markerAlpha *= 0.90f;
                postInvalidateOnAnimation();
            }
        }
    }

    // =========================================================================
    // Đồ thị đường cong gia tốc Interpolator
    // =========================================================================
    private static class InterpolatorGraphView extends View {
        private final float density;
        private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path curvePath = new Path();

        private Interpolator interpolator = new AccelerateDecelerateInterpolator();
        private float progress = 0f;

        InterpolatorGraphView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;

            guidePaint.setColor(Color.parseColor("#22FFFFFF"));
            guidePaint.setStrokeWidth(1f * density);
            guidePaint.setStyle(Paint.Style.STROKE);
            guidePaint.setPathEffect(new DashPathEffect(new float[]{4 * density, 4 * density}, 0));

            curvePaint.setColor(Color.parseColor("#818CF8"));
            curvePaint.setStyle(Paint.Style.STROKE);
            curvePaint.setStrokeWidth(2.4f * density);
            curvePaint.setStrokeCap(Paint.Cap.ROUND);

            dotPaint.setStyle(Paint.Style.FILL);
        }

        void setInterpolator(Interpolator interp) {
            this.interpolator = interp;
            invalidate();
        }

        void setProgress(float t) {
            this.progress = Math.max(0f, Math.min(1f, t));
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            float padX = 12f * density;
            float padY = 12f * density;
            float plotW = w - padX * 2f;
            float plotH = h - padY * 2f;

            float y0 = h - padY;
            float y1 = padY;
            canvas.drawLine(padX, y0, w - padX, y0, guidePaint);
            canvas.drawLine(padX, y1, w - padX, y1, guidePaint);

            curvePath.reset();
            int samples = 75;
            for (int i = 0; i <= samples; i++) {
                float t = i / (float) samples;
                float ft = interpolator.getInterpolation(t);
                float x = padX + t * plotW;
                float y = y0 - ft * plotH;
                if (i == 0) curvePath.moveTo(x, y);
                else curvePath.lineTo(x, y);
            }
            canvas.drawPath(curvePath, curvePaint);

            float curFt = interpolator.getInterpolation(progress);
            float cx = padX + progress * plotW;
            float cy = y0 - curFt * plotH;

            dotPaint.setColor(Color.parseColor("#38BDF8"));
            canvas.drawCircle(cx, cy, 4.5f * density, dotPaint);
        }
    }
}
