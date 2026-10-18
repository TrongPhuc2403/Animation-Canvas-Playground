package com.example.animationcanvas;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.animationcanvas.animation.AnimationActivity;
import com.example.animationcanvas.drawing.DrawingActivity;
import com.example.animationcanvas.particle.ParticleActivity;

import java.util.Random;

/**
 * Màn hình chính:
 * - Gồm hộp đồ chơi vật lý tương tác (ToyBoxBannerView) ngay dưới tiêu đề: các khối hình học
 *   tự nảy, xoay và bật tung lên khi người dùng chạm vào.
 * - Mỗi thẻ chức năng (Card) đều chứa một hình minh họa động (MiniCanvasPreview) chạy trực tiếp
 *   để thể hiện đúng bản chất của từng bài demo.
 */
public class MainActivity extends AppCompatActivity {

    private ObjectAnimator logoPulse;
    private ObjectAnimator logoSpin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FrameLayout bannerHolder = findViewById(R.id.bannerToyBox);
        bannerHolder.addView(new ToyBoxBannerView(this));

        attachCardPreview(R.id.previewAnimation, 0);
        attachCardPreview(R.id.previewDrawing, 1);
        attachCardPreview(R.id.previewParticle, 2);

        setupCard(R.id.cardAnimation, AnimationActivity.class, 0);
        setupCard(R.id.cardDrawing, DrawingActivity.class, 1);
        setupCard(R.id.cardParticle, ParticleActivity.class, 2);

        startHeaderAnimation();
    }

    private void attachCardPreview(int containerId, int type) {
        FrameLayout container = findViewById(containerId);
        container.addView(new CardMiniPreviewView(this, type));
    }

    private void setupCard(int cardId, Class<?> target, int index) {
        View card = findViewById(cardId);

        card.setAlpha(0f);
        card.setTranslationY(90f);
        card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(180L + index * 110L)
                .setDuration(520)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .start();

        card.setOnTouchListener(this::onCardTouch);
        card.setOnClickListener(v -> startActivity(new Intent(this, target)));
    }

    private boolean onCardTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(110).start();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                v.animate().scaleX(1f).scaleY(1f).setDuration(280)
                        .setInterpolator(new OvershootInterpolator(2.6f)).start();
                break;
        }
        return false;
    }

    private void startHeaderAnimation() {
        View logo = findViewById(R.id.logoContainer);
        View icon = findViewById(R.id.ivLogo);
        View title = findViewById(R.id.tvMainTitle);
        View subtitle = findViewById(R.id.tvMainSubtitle);
        View banner = findViewById(R.id.bannerToyBox);

        logo.setScaleX(0f);
        logo.setScaleY(0f);
        logo.animate().scaleX(1f).scaleY(1f).setDuration(550)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();

        title.setAlpha(0f);
        title.setTranslationX(-40f);
        subtitle.setAlpha(0f);
        AnimatorSet titleSet = new AnimatorSet();
        titleSet.playTogether(
                ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(title, View.TRANSLATION_X, -40f, 0f));
        titleSet.setStartDelay(100);
        titleSet.setDuration(450);
        titleSet.setInterpolator(new DecelerateInterpolator());
        titleSet.start();
        subtitle.animate().alpha(1f).setStartDelay(220).setDuration(450).start();

        banner.setAlpha(0f);
        banner.setScaleX(0.94f);
        banner.setScaleY(0.94f);
        banner.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(140).setDuration(500)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();

        logoPulse = ObjectAnimator.ofPropertyValuesHolder(logo,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f));
        logoPulse.setStartDelay(650);
        logoPulse.setDuration(1300);
        logoPulse.setRepeatCount(ValueAnimator.INFINITE);
        logoPulse.setRepeatMode(ValueAnimator.REVERSE);
        logoPulse.start();

        logoSpin = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f);
        logoSpin.setDuration(8500);
        logoSpin.setRepeatCount(ValueAnimator.INFINITE);
        logoSpin.setInterpolator(new LinearInterpolator());
        logoSpin.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (logoPulse != null) logoPulse.resume();
        if (logoSpin != null) logoSpin.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (logoPulse != null) logoPulse.pause();
        if (logoSpin != null) logoSpin.pause();
    }

    @Override
    protected void onDestroy() {
        if (logoPulse != null) logoPulse.cancel();
        if (logoSpin != null) logoSpin.cancel();
        super.onDestroy();
    }

    // =========================================================================
    // Hộp đồ chơi vật lý tương tác ở đầu trang chủ (chạm để các khối hình bật nhảy)
    // =========================================================================
    private static class ToyBoxBannerView extends View {
        private static final int COUNT = 5;
        private final float density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path starPath = new Path();

        private final float[] x = new float[COUNT];
        private final float[] y = new float[COUNT];
        private final float[] vx = new float[COUNT];
        private final float[] vy = new float[COUNT];
        private final float[] rot = new float[COUNT];
        private final float[] vRot = new float[COUNT];
        private final float[] radius = new float[COUNT];
        private final int[] colors = {
                Color.parseColor("#6366F1"), // Tím Indigo
                Color.parseColor("#F43F5E"), // Hồng Coral
                Color.parseColor("#F59E0B"), // Vàng Amber
                Color.parseColor("#10B981"), // Xanh Ngọc
                Color.parseColor("#38BDF8")  // Xanh Sky
        };

        private float rippleX = -1f, rippleY = -1f, rippleR = 0f, rippleAlpha = 0f;
        private boolean initialized = false;
        private long lastNanos = 0L;

        ToyBoxBannerView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            hintPaint.setColor(Color.parseColor("#7094A3B8"));
            hintPaint.setTextSize(11.5f * density);
            hintPaint.setTextAlign(Paint.Align.RIGHT);
        }

        private void initShapes(int w, int h) {
            Random rnd = new Random();
            for (int i = 0; i < COUNT; i++) {
                radius[i] = (17f + (i % 2) * 3f) * density;
                x[i] = w * (0.14f + i * 0.18f);
                y[i] = h * (0.3f + rnd.nextFloat() * 0.3f);
                vx[i] = ((rnd.nextBoolean() ? 1 : -1) * (55f + rnd.nextFloat() * 45f)) * density;
                vy[i] = (-60f - rnd.nextFloat() * 80f) * density;
                vRot[i] = (rnd.nextBoolean() ? 1 : -1) * (70f + rnd.nextFloat() * 70f);
            }
            initialized = true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float tx = event.getX();
                float ty = event.getY();
                rippleX = tx;
                rippleY = ty;
                rippleR = 10f * density;
                rippleAlpha = 0.8f;

                for (int i = 0; i < COUNT; i++) {
                    float dx = x[i] - tx;
                    float dy = y[i] - ty;
                    float dist = (float) Math.hypot(dx, dy);
                    if (dist < 140f * density) {
                        float nx = dist > 1f ? dx / dist : 0f;
                        vx[i] += nx * 220f * density;
                        vy[i] = -240f * density;
                        vRot[i] = (nx >= 0 ? 180f : -180f);
                    }
                }
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;
            if (!initialized) initShapes(w, h);

            long now = System.nanoTime();
            float dt = lastNanos == 0L ? 1f / 60f : (now - lastNanos) / 1_000_000_000f;
            lastNanos = now;
            if (dt > 0.04f) dt = 0.04f;

            float gravity = 360f * density;

            // Vẽ dòng gợi ý nhỏ góc phải trên
            canvas.drawText("Chạm để tương tác", w - 14f * density, 20f * density, hintPaint);

            // Sóng chạm
            if (rippleAlpha > 0.02f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f * density);
                paint.setColor(Color.WHITE);
                paint.setAlpha((int) (rippleAlpha * 140));
                canvas.drawCircle(rippleX, rippleY, rippleR, paint);
                rippleR += 220f * density * dt;
                rippleAlpha *= 0.90f;
            }

            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < COUNT; i++) {
                vy[i] += gravity * dt;
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
                rot[i] += vRot[i] * dt;

                float r = radius[i];
                if (x[i] < r) { x[i] = r; vx[i] = Math.abs(vx[i]) * 0.9f; }
                else if (x[i] > w - r) { x[i] = w - r; vx[i] = -Math.abs(vx[i]) * 0.9f; }

                if (y[i] > h - r - 4f * density) {
                    y[i] = h - r - 4f * density;
                    vy[i] = -Math.max(Math.abs(vy[i]) * 0.78f, 150f * density);
                } else if (y[i] < r) {
                    y[i] = r;
                    vy[i] = Math.abs(vy[i]) * 0.8f;
                }

                canvas.save();
                canvas.translate(x[i], y[i]);
                canvas.rotate(rot[i]);
                paint.setColor(colors[i]);

                switch (i % 4) {
                    case 0: // Quả bóng tròn có 2 mắt trắng
                        canvas.drawCircle(0, 0, r, paint);
                        paint.setColor(Color.WHITE);
                        canvas.drawCircle(-r * 0.28f, -r * 0.15f, r * 0.18f, paint);
                        canvas.drawCircle(r * 0.28f, -r * 0.15f, r * 0.18f, paint);
                        break;
                    case 1: // Khối vuông bo góc
                        canvas.drawRoundRect(-r * 0.85f, -r * 0.85f, r * 0.85f, r * 0.85f,
                                r * 0.32f, r * 0.32f, paint);
                        break;
                    case 2: // Ngôi sao 5 cánh
                        buildStar(starPath, r);
                        canvas.drawPath(starPath, paint);
                        break;
                    default: // Vòng khuyên (Donut)
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(r * 0.42f);
                        canvas.drawCircle(0, 0, r * 0.72f, paint);
                        paint.setStyle(Paint.Style.FILL);
                        break;
                }
                canvas.restore();
            }

            postInvalidateOnAnimation();
        }

        private void buildStar(Path path, float outerR) {
            path.reset();
            float innerR = outerR * 0.48f;
            for (int i = 0; i < 10; i++) {
                float rad = (i % 2 == 0) ? outerR : innerR;
                double a = -Math.PI / 2 + i * Math.PI / 5;
                float px = (float) (Math.cos(a) * rad);
                float py = (float) (Math.sin(a) * rad);
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            path.close();
        }
    }

    // =========================================================================
    // Hình minh họa động thu nhỏ bên trong mỗi Card
    // =========================================================================
    private static class CardMiniPreviewView extends View {
        private final int type;
        private final float density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path wavePath = new Path();
        private float time = 0f;
        private long lastNanos = 0L;

        CardMiniPreviewView(Context context, int type) {
            super(context);
            this.type = type;
            this.density = getResources().getDisplayMetrics().density;
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

            if (type == 0) {
                // Card 1: Quả bóng nảy đàn hồi (Squash & Stretch)
                float bounce = Math.abs((float) Math.sin(time * 3.4f));
                float ballY = cy + 12f * density - bounce * 22f * density;
                float sx = bounce < 0.2f ? 1.25f : 0.92f;
                float sy = bounce < 0.2f ? 0.78f : 1.08f;

                // Bóng đổ dưới sàn
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb((int) ((1f - bounce * 0.6f) * 110), 0, 0, 0));
                canvas.drawOval(cx - (10f - bounce * 4f) * density, cy + 16f * density,
                        cx + (10f - bounce * 4f) * density, cy + 20f * density, paint);

                canvas.save();
                canvas.translate(cx, ballY);
                canvas.scale(sx, sy);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(0, 0, 9.5f * density, paint);
                canvas.restore();

            } else if (type == 1) {
                // Card 2: Nét cọ tự vẽ đường cong vô cực mượt mà
                wavePath.reset();
                int steps = 28;
                for (int i = 0; i <= steps; i++) {
                    float t = time * 2.4f - i * 0.08f;
                    float px = cx + (float) Math.sin(t) * 16f * density;
                    float py = cy + (float) Math.sin(t * 2f) * 10f * density;
                    if (i == 0) wavePath.moveTo(px, py);
                    else wavePath.lineTo(px, py);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(3.2f * density);
                paint.setColor(Color.WHITE);
                canvas.drawPath(wavePath, paint);

                // Đầu bút đang vẽ
                float headX = cx + (float) Math.sin(time * 2.4f) * 16f * density;
                float headY = cy + (float) Math.sin(time * 4.8f) * 10f * density;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#FEF08A"));
                canvas.drawCircle(headX, headY, 4.5f * density, paint);

            } else {
                // Card 3: Chùm pháo hoa bung nở liên tục
                float cycle = (time * 0.9f) % 1f;
                float maxR = 22f * density;
                float curR = cycle * maxR;
                int alpha = (int) ((1f - cycle) * 255);

                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4 + time * 0.4f;
                    float px = cx + (float) Math.cos(angle) * curR;
                    float py = cy + (float) Math.sin(angle) * curR + cycle * cycle * 6f * density;
                    paint.setColor(i % 2 == 0 ? Color.WHITE : Color.parseColor("#FDE047"));
                    paint.setAlpha(alpha);
                    canvas.drawCircle(px, py, (2.8f - cycle * 1.4f) * density, paint);
                }
            }

            postInvalidateOnAnimation();
        }
    }
}
