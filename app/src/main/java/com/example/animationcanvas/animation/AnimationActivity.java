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
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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
 * Màn hình minh họa Property Animation theo chuẩn Motion Inspector Studio:
 * - Sân khấu Viewport tích hợp thước đo tọa độ (Rulers), dư ảnh Onion-Skinning và
 *   thanh thông số thời gian thực (TX, TY, ROT, SCALE, ALPHA).
 * - Vật thể cơ học đa lớp (KineticObjectView) với 4 kiểu thiết kế hình học chuẩn xác.
 * - Đồ thị đường cong gia tốc Interpolator hiển thị miền diện tích và con trỏ thời gian thực.
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

    private final List<MaterialButton> modeButtons = new ArrayList<>();

    private KineticObjectView mascotView;
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

        mascotView = new KineticObjectView(this);
        target.addView(mascotView);

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
        setupModeButtons();

        findViewById(R.id.btnReset).setOnClickListener(v -> {
            highlightModeButton(null);
            resetTarget(true);
        });

        target.setOnClickListener(v -> v.animate()
                .rotationBy(360f)
                .scaleX(1.16f).scaleY(1.16f)
                .setDuration(420)
                .setInterpolator(new OvershootInterpolator(2.2f))
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

    private void setupModeButtons() {
        int[] ids = {
                R.id.btnMove, R.id.btnRotate, R.id.btnScale, R.id.btnFade,
                R.id.btnBounce, R.id.btnOrbit, R.id.btnShake, R.id.btnCombo
        };
        Runnable[] actions = {
                this::playMove, this::playRotate, this::playScale, this::playFade,
                this::playBounce, this::playOrbit, this::playWave, this::playCombo
        };
        for (int i = 0; i < ids.length; i++) {
            MaterialButton btn = findViewById(ids[i]);
            final Runnable action = actions[i];
            modeButtons.add(btn);
            btn.setOnClickListener(v -> {
                highlightModeButton(btn);
                action.run();
            });
        }
        highlightModeButton(null);
    }

    private void highlightModeButton(MaterialButton active) {
        int activeBg = Color.parseColor("#FFFF6B4A");
        int activeStroke = Color.parseColor("#FFFF866B");
        int idleBg = Color.parseColor("#FF1E212B");
        int idleStroke = Color.parseColor("#FF2E3240");
        int comboStroke = Color.parseColor("#FF6366F1");

        for (MaterialButton b : modeButtons) {
            boolean isCurrent = (b == active);
            b.setBackgroundTintList(ColorStateList.valueOf(isCurrent ? activeBg : idleBg));
            if (isCurrent) {
                b.setStrokeColor(ColorStateList.valueOf(activeStroke));
                b.setTextColor(Color.WHITE);
                b.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70)
                        .withEndAction(() -> b.animate().scaleX(1f).scaleY(1f).setDuration(180)
                                .setInterpolator(new OvershootInterpolator(2.5f)).start())
                        .start();
            } else {
                boolean isCombo = b.getId() == R.id.btnCombo;
                b.setStrokeColor(ColorStateList.valueOf(isCombo ? comboStroke : idleStroke));
                b.setTextColor(Color.parseColor(isCombo ? "#FFA5B4FC" : "#FFF4F4F6"));
            }
        }
    }

    private void cycleTargetShape() {
        shapeIndex = (shapeIndex + 1) % 4;
        String[] labels = {
                "Vật thể: Lăng kính",
                "Vật thể: Con quay",
                "Vật thể: Phi thuyền",
                "Vật thể: Bánh răng"
        };
        btnSwitchShape.setText(labels[shapeIndex]);
        mascotView.setCharacterType(shapeIndex);

        target.animate().scaleX(0.78f).scaleY(0.78f).setDuration(85)
                .withEndAction(() -> target.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new OvershootInterpolator(2.6f)).start())
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
            chip.setTextSize(11.5f);
            chip.setOnClickListener(v -> {
                interpolator = createInterpolator(index);
                interpolatorName = INTERPOLATOR_NAMES[index];
                interpGraph.setInterpolator(interpolator);
                highlightModeButton(findViewById(R.id.btnMove));
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
    // 8 Chế độ Animation
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
        PropertyValuesHolder sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.75f, 0.78f, 1f);
        PropertyValuesHolder sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.75f, 0.78f, 1f);
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
                "PropertyValuesHolder.ofFloat(\"scaleX/Y\", 1f, 1.75f, 1f)");
    }

    private void playFade() {
        ObjectAnimator fade = ObjectAnimator.ofFloat(target, View.ALPHA, 1f, 0.08f, 1f);
        fade.setDuration(duration * 2);
        fade.setInterpolator(interpolator);
        applyRepeat(fade);

        ObjectAnimator shadowFade = ObjectAnimator.ofFloat(shadow, View.ALPHA, 0.35f, 0.05f, 0.35f);
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
        final float ry = maxVerticalDistance() * 0.62f;
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
                "ValueAnimator.ofFloat(0f, 2*PI) // Quỹ đạo elip (sin, cos)");
    }

    private void playWave() {
        float d = maxHorizontalDistance() * 0.75f;
        float h = maxVerticalDistance() * 0.52f;
        PropertyValuesHolder tx = PropertyValuesHolder.ofFloat(View.TRANSLATION_X,
                0f, d * 0.5f, d, d * 0.5f, 0f, -d * 0.5f, -d, -d * 0.5f, 0f);
        PropertyValuesHolder ty = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y,
                0f, -h, 0f, h, 0f, -h, 0f, h, 0f);
        PropertyValuesHolder rot = PropertyValuesHolder.ofFloat(View.ROTATION,
                0f, 22f, -22f, 16f, -16f, 10f, -10f, 0f);
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
    // Điều phối & Telemetry
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
            mascotView.setVelocity(curTx - prevTx, curTy - prevTy);
            prevTx = curTx;
            prevTy = curTy;

            float cx = stage.getWidth() / 2f + curTx;
            float cy = stage.getHeight() / 2f + curTy;
            stageBackdrop.recordFrame(cx, cy, target.getRotation(),
                    target.getScaleX(), target.getAlpha(),
                    curTx, curTy);
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentAnimator == animation) {
                    currentAnimator = null;
                    mascotView.setVelocity(0f, 0f);
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
        mascotView.setVelocity(0f, 0f);

        if (animated) {
            target.animate().translationX(0f).translationY(0f)
                    .rotation(0f).rotationX(0f).rotationY(0f)
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(300).setInterpolator(new OvershootInterpolator(1.4f)).start();
            shadow.animate().translationX(0f).translationY(0f).scaleX(1f).alpha(0.35f).setDuration(300).start();
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
            shadow.setAlpha(0.35f);
        }
    }

    private float maxHorizontalDistance() {
        return Math.max(0f, (stage.getWidth() - target.getWidth()) / 2f - dp(18));
    }

    private float maxVerticalDistance() {
        return Math.max(0f, (stage.getHeight() - target.getHeight()) / 2f - dp(32));
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
    // Vật thể cơ học đa lớp (KineticObjectView)
    // =========================================================================
    private static class KineticObjectView extends View {
        private final float density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shapePath = new Path();
        private final RectF arcRect = new RectF();
        private int charType = 0;
        private float vx = 0f, vy = 0f;
        private float time = 0f;
        private long lastNanos = 0L;

        KineticObjectView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
        }

        void setCharacterType(int type) {
            this.charType = type;
            invalidate();
        }

        void setVelocity(float vx, float vy) {
            float limit = 12f * density;
            this.vx = Math.max(-limit, Math.min(limit, vx));
            this.vy = Math.max(-limit, Math.min(limit, vy));
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
            float r = Math.min(w, h) * 0.40f;

            // Vòng đo góc cơ học ngoài cùng
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f * density);
            paint.setColor(Color.parseColor("#38FFFFFF"));
            canvas.drawCircle(cx, cy, r * 1.14f, paint);

            // Hạt mốc xoay trên quỹ đạo
            float orbA = time * 2.2f;
            float ox = cx + (float) Math.cos(orbA) * r * 1.14f;
            float oy = cy + (float) Math.sin(orbA) * r * 1.14f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#FFFF6B4A"));
            canvas.drawCircle(ox, oy, 2.8f * density, paint);

            if (charType == 0) {
                // Kiểu 0: Khối Lăng kính Quang học đa lớp (Prism Squircle)
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#FFFF6B4A"));
                canvas.drawRoundRect(cx - r, cy - r, cx + r, cy + r,
                        r * 0.42f, r * 0.42f, paint);

                // Viền sáng nội thất
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * density);
                paint.setColor(Color.parseColor("#55FFFFFF"));
                canvas.drawRoundRect(cx - r * 0.82f, cy - r * 0.82f,
                        cx + r * 0.82f, cy + r * 0.82f,
                        r * 0.32f, r * 0.32f, paint);

                // Khối thoi trung tâm lệch nhẹ theo vận tốc (Parallax Core)
                float px = cx + vx * 0.35f;
                float py = cy + vy * 0.35f;
                float d = r * 0.44f;
                shapePath.reset();
                shapePath.moveTo(px, py - d);
                shapePath.lineTo(px + d, py);
                shapePath.lineTo(px, py + d);
                shapePath.lineTo(px - d, py);
                shapePath.close();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#FF15171E"));
                canvas.drawPath(shapePath, paint);

                paint.setColor(Color.WHITE);
                canvas.drawCircle(px, py, r * 0.14f, paint);

            } else if (charType == 1) {
                // Kiểu 1: Con quay Gyroscope 3 vòng đồng tâm
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#FF1E2230"));
                canvas.drawCircle(cx, cy, r, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(3.2f * density);
                paint.setColor(Color.parseColor("#FF6366F1"));
                arcRect.set(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f);
                canvas.drawArc(arcRect, (time * 95f) % 360f, 250f, false, paint);

                paint.setColor(Color.parseColor("#FFFF6B4A"));
                paint.setStrokeWidth(2.6f * density);
                arcRect.set(cx - r * 0.6f, cy - r * 0.6f, cx + r * 0.6f, cy + r * 0.6f);
                canvas.drawArc(arcRect, (-time * 140f) % 360f, 220f, false, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(cx + vx * 0.3f, cy + vy * 0.3f, r * 0.22f, paint);

            } else if (charType == 2) {
                // Kiểu 2: Phi thuyền Vector (Delta Craft)
                canvas.save();
                canvas.translate(cx, cy);
                shapePath.reset();
                shapePath.moveTo(0, -r * 1.05f);
                shapePath.lineTo(r * 0.88f, r * 0.85f);
                shapePath.lineTo(0, r * 0.45f);
                shapePath.lineTo(-r * 0.88f, r * 0.85f);
                shapePath.close();

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#FF10B981"));
                canvas.drawPath(shapePath, paint);

                // Buồng lái
                paint.setColor(Color.parseColor("#FF111318"));
                canvas.drawOval(-r * 0.22f, -r * 0.38f, r * 0.22f, r * 0.22f, paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(0, -r * 0.14f, r * 0.09f, paint);
                canvas.restore();

            } else {
                // Kiểu 3: Bánh răng cơ học 8 đỉnh (Precision Star Gear)
                buildGearStar(shapePath, cx, cy, r * 1.04f, r * 0.72f, 8, time * 0.6f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#FFF59E0B"));
                canvas.drawPath(shapePath, paint);

                paint.setColor(Color.parseColor("#FF15171E"));
                canvas.drawCircle(cx, cy, r * 0.45f, paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(cx + vx * 0.25f, cy + vy * 0.25f, r * 0.18f, paint);
            }

            postInvalidateOnAnimation();
        }

        private void buildGearStar(Path path, float cx, float cy, float outerR, float innerR, int points, float phase) {
            path.reset();
            int total = points * 2;
            for (int i = 0; i < total; i++) {
                float rad = (i % 2 == 0) ? outerR : innerR;
                double a = phase + i * Math.PI / points;
                float px = cx + (float) (Math.cos(a) * rad);
                float py = cy + (float) (Math.sin(a) * rad);
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            path.close();
        }
    }

    // =========================================================================
    // Sân khấu Viewport: Thước đo Rulers + Dư ảnh Onion-Skinning + Telemetry Bar
    // =========================================================================
    private static class StageBackdropView extends View {
        private static final int MAX_TRAIL = 36;

        private final float density;
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rulerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint onionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path trailPath = new Path();

        // Mỗi phần tử: {x, y, rot, scale}
        private final List<float[]> trail = new ArrayList<>();
        private float markerX = -1f, markerY = -1f, markerAlpha = 0f, markerRadius = 0f;
        private float liveTx = 0f, liveTy = 0f, liveRot = 0f, liveScale = 1f, liveAlpha = 1f;

        StageBackdropView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;

            gridPaint.setColor(Color.parseColor("#16FFFFFF"));
            gridPaint.setStrokeWidth(1f);

            axisPaint.setColor(Color.parseColor("#14FFFFFF"));
            axisPaint.setStrokeWidth(1f);

            rulerPaint.setColor(Color.parseColor("#36FFFFFF"));
            rulerPaint.setStrokeWidth(1f);

            trailPaint.setStyle(Paint.Style.STROKE);
            trailPaint.setStrokeCap(Paint.Cap.ROUND);
            trailPaint.setStrokeJoin(Paint.Join.ROUND);
            trailPaint.setStrokeWidth(2f * density);
            trailPaint.setColor(Color.parseColor("#88FF6B4A"));

            onionPaint.setStyle(Paint.Style.STROKE);
            onionPaint.setStrokeWidth(1.2f * density);
            onionPaint.setColor(Color.parseColor("#FFFF6B4A"));

            markerPaint.setStyle(Paint.Style.STROKE);
            markerPaint.setStrokeWidth(1.8f * density);
            markerPaint.setColor(Color.parseColor("#FFFF6B4A"));

            hudPaint.setColor(Color.parseColor("#999EA4B4"));
            hudPaint.setTextSize(10f * density);
            hudPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        }

        void recordFrame(float x, float y, float rot, float scale, float alpha, float tx, float ty) {
            if (trail.size() >= MAX_TRAIL) trail.remove(0);
            trail.add(new float[]{x, y, rot, scale});
            liveTx = tx / density;
            liveTy = ty / density;
            liveRot = rot;
            liveScale = scale;
            liveAlpha = alpha;
            invalidate();
        }

        void clearTrail() {
            trail.clear();
            liveTx = 0f;
            liveTy = 0f;
            liveRot = 0f;
            liveScale = 1f;
            liveAlpha = 1f;
            invalidate();
        }

        void spawnTargetMarker(float x, float y) {
            markerX = x;
            markerY = y;
            markerRadius = 6f * density;
            markerAlpha = 1f;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            float cx = w / 2f;
            float cy = h / 2f;

            // Trục tọa độ trung tâm Viewport
            canvas.drawLine(0, cy, w, cy, axisPaint);
            canvas.drawLine(cx, 0, cx, h, axisPaint);

            // Lưới chấm kỹ thuật
            float step = 28f * density;
            for (float x = step; x < w; x += step) {
                for (float y = step; y < h; y += step) {
                    canvas.drawCircle(x, y, 1.1f * density, gridPaint);
                }
            }

            // Vạch thước đo Rulers cạnh trái và cạnh dưới
            float tickStep = 14f * density;
            int tickIdx = 0;
            for (float x = tickStep; x < w - tickStep; x += tickStep) {
                float len = (tickIdx % 5 == 0) ? 6f * density : 3f * density;
                canvas.drawLine(x, h - len, x, h, rulerPaint);
                tickIdx++;
            }

            // Quỹ đạo & Dư ảnh Onion-Skinning
            int n = trail.size();
            if (n > 1) {
                trailPath.reset();
                trailPath.moveTo(trail.get(0)[0], trail.get(0)[1]);
                for (int i = 1; i < n; i++) {
                    float[] pt = trail.get(i);
                    trailPath.lineTo(pt[0], pt[1]);
                }
                canvas.drawPath(trailPath, trailPaint);

                // Vẽ 4 khung dư ảnh (Onion-Skin Ghosts) dọc theo quỹ đạo
                for (int i = Math.max(0, n - 24); i < n - 3; i += 6) {
                    float ratio = (i + 1f) / n;
                    float[] pt = trail.get(i);
                    float sz = 18f * density * pt[3];
                    canvas.save();
                    canvas.translate(pt[0], pt[1]);
                    canvas.rotate(pt[2]);
                    onionPaint.setAlpha((int) (ratio * 85));
                    canvas.drawRoundRect(-sz, -sz, sz, sz, 7f * density, 7f * density, onionPaint);
                    canvas.restore();
                }
            }

            // Vòng tâm điểm khi chạm
            if (markerAlpha > 0.02f) {
                markerPaint.setAlpha((int) (markerAlpha * 210));
                canvas.drawCircle(markerX, markerY, markerRadius, markerPaint);
                markerRadius += 2.0f * density;
                markerAlpha *= 0.89f;
                postInvalidateOnAnimation();
            }

            // Thanh Telemetry thời gian thực góc dưới sân khấu
            String telemetry = String.format(Locale.US,
                    "X:%+4.0fdp  Y:%+4.0fdp  ROT:%3.0f°  SCL:%.2fx  ALP:%.2f",
                    liveTx, liveTy, liveRot % 360f, liveScale, liveAlpha);
            canvas.drawText(telemetry, 14f * density, h - 12f * density, hudPaint);
        }
    }

    // =========================================================================
    // Đồ thị đường cong gia tốc Interpolator (Oscilloscope View)
    // =========================================================================
    private static class InterpolatorGraphView extends View {
        private final float density;
        private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path curvePath = new Path();
        private final Path fillPath = new Path();

        private Interpolator interpolator = new AccelerateDecelerateInterpolator();
        private float progress = 0f;

        InterpolatorGraphView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;

            guidePaint.setColor(Color.parseColor("#22FFFFFF"));
            guidePaint.setStrokeWidth(1f * density);
            guidePaint.setStyle(Paint.Style.STROKE);
            guidePaint.setPathEffect(new DashPathEffect(new float[]{4 * density, 4 * density}, 0));

            curvePaint.setColor(Color.parseColor("#FFFF6B4A"));
            curvePaint.setStrokeWidth(2.2f * density);
            curvePaint.setStyle(Paint.Style.STROKE);
            curvePaint.setStrokeCap(Paint.Cap.ROUND);

            cursorPaint.setColor(Color.parseColor("#44FFFFFF"));
            cursorPaint.setStrokeWidth(1f * density);

            dotPaint.setStyle(Paint.Style.FILL);

            labelPaint.setColor(Color.parseColor("#999EA4B4"));
            labelPaint.setTextSize(9.5f * density);
            labelPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        }

        void setInterpolator(Interpolator interp) {
            this.interpolator = interp;
            invalidate();
        }

        void setProgress(float fraction) {
            this.progress = Math.max(0f, Math.min(1f, fraction));
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                    Color.parseColor("#38FF6B4A"),
                    Color.parseColor("#00FF6B4A"),
                    Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            float padX = 10f * density;
            float padY = 12f * density;
            float usableW = w - padX * 2f;
            float usableH = h - padY * 2f;

            // Đường chuẩn y = 0 và y = 1
            canvas.drawLine(padX, padY, w - padX, padY, guidePaint);
            canvas.drawLine(padX, h - padY, w - padX, h - padY, guidePaint);

            curvePath.reset();
            fillPath.reset();
            int samples = 64;
            for (int i = 0; i <= samples; i++) {
                float t = i / (float) samples;
                float v = interpolator.getInterpolation(t);
                float x = padX + t * usableW;
                float y = (h - padY) - v * usableH;
                if (i == 0) {
                    curvePath.moveTo(x, y);
                    fillPath.moveTo(x, h - padY);
                    fillPath.lineTo(x, y);
                } else {
                    curvePath.lineTo(x, y);
                    fillPath.lineTo(x, y);
                }
            }
            fillPath.lineTo(w - padX, h - padY);
            fillPath.close();

            canvas.drawPath(fillPath, fillPaint);
            canvas.drawPath(curvePath, curvePaint);

            float curV = interpolator.getInterpolation(progress);
            float dotX = padX + progress * usableW;
            float dotY = (h - padY) - curV * usableH;

            // Đường quét dọc (Playhead)
            canvas.drawLine(dotX, 4f * density, dotX, h - 4f * density, cursorPaint);

            dotPaint.setColor(Color.parseColor("#55FF6B4A"));
            canvas.drawCircle(dotX, dotY, 7f * density, dotPaint);
            dotPaint.setColor(Color.WHITE);
            canvas.drawCircle(dotX, dotY, 3.2f * density, dotPaint);

            String info = String.format(Locale.US, "t=%.2f  v=%.2f", progress, curV);
            canvas.drawText(info, padX + 4f * density, padY + 10f * density, labelPaint);
        }
    }
}
