package com.example.animationcanvas;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.animationcanvas.animation.AnimationActivity;
import com.example.animationcanvas.drawing.DrawingActivity;
import com.example.animationcanvas.particle.ParticleActivity;

/**
 * Màn hình chính theo phong cách Motion Design Studio chuyên nghiệp:
 * - Biểu tượng KineticEmblemView chuyển động cơ học thời gian thực.
 * - Bàn thử nghiệm vật lý lò xo và sóng điều hòa (SpringWaveFieldView) phản hồi đa điểm theo ngón tay.
 * - Ba thẻ chức năng tích hợp cửa sổ Viewport mô phỏng trực tiếp (60 FPS) từng kỹ thuật:
 *   Onion-Skin Keyframe Animation, Bezier Path Vector Drawing và Hệ thống hạt vật lý.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FrameLayout logoHolder = findViewById(R.id.logoContainer);
        logoHolder.addView(new KineticEmblemView(this));

        FrameLayout bannerHolder = findViewById(R.id.bannerToyBox);
        bannerHolder.addView(new SpringWaveFieldView(this));

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
        card.setTranslationY(64f);
        card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(140L + index * 95L)
                .setDuration(480)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();

        card.setOnTouchListener(this::onCardTouch);
        card.setOnClickListener(v -> startActivity(new Intent(this, target)));
    }

    private boolean onCardTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                v.animate().scaleX(1f).scaleY(1f).setDuration(240)
                        .setInterpolator(new OvershootInterpolator(2.0f)).start();
                break;
        }
        return false;
    }

    private void startHeaderAnimation() {
        View logo = findViewById(R.id.logoContainer);
        View title = findViewById(R.id.tvMainTitle);
        View subtitle = findViewById(R.id.tvMainSubtitle);
        View banner = findViewById(R.id.bannerToyBox);

        logo.setScaleX(0.6f);
        logo.setScaleY(0.6f);
        logo.setAlpha(0f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(460)
                .setInterpolator(new OvershootInterpolator(1.8f)).start();

        title.setAlpha(0f);
        title.setTranslationX(-24f);
        subtitle.setAlpha(0f);
        AnimatorSet titleSet = new AnimatorSet();
        titleSet.playTogether(
                ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(title, View.TRANSLATION_X, -24f, 0f));
        titleSet.setStartDelay(80);
        titleSet.setDuration(400);
        titleSet.setInterpolator(new DecelerateInterpolator());
        titleSet.start();
        subtitle.animate().alpha(1f).setStartDelay(180).setDuration(400).start();

        banner.setAlpha(0f);
        banner.setTranslationY(24f);
        banner.animate().alpha(1f).translationY(0f)
                .setStartDelay(120).setDuration(460)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
    }

    // =========================================================================
    // Biểu tượng cơ học Kinetic Emblem ở góc trên bên trái
    // =========================================================================
    private static class KineticEmblemView extends View {
        private final float density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path diamondPath = new Path();
        private final RectF arcRect = new RectF();
        private float time = 0f;
        private long lastNanos = 0L;

        KineticEmblemView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
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
            float rOuter = Math.min(w, h) * 0.34f;
            float rInner = rOuter * 0.62f;

            // Vòng quỹ đạo ngoài xoay thuận
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(1.8f * density);
            paint.setColor(Color.parseColor("#FF6366F1"));
            arcRect.set(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter);
            canvas.drawArc(arcRect, (time * 75f) % 360f, 230f, false, paint);

            // Vòng quỹ đạo trong xoay ngược
            paint.setColor(Color.parseColor("#FFFF6B4A"));
            arcRect.set(cx - rInner, cy - rInner, cx + rInner, cy + rInner);
            canvas.drawArc(arcRect, (-time * 110f) % 360f, 200f, false, paint);

            // Hạt vệ tinh trên vành ngoài
            double a = Math.toRadians((time * 75f) % 360f);
            float sx = cx + (float) Math.cos(a) * rOuter;
            float sy = cy + (float) Math.sin(a) * rOuter;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(sx, sy, 2.2f * density, paint);

            // Điểm Keyframe hình thoi nhịp nhàng ở tâm
            float pulse = 1f + 0.16f * (float) Math.sin(time * 4.2f);
            float d = 4.2f * density * pulse;
            diamondPath.reset();
            diamondPath.moveTo(cx, cy - d);
            diamondPath.lineTo(cx + d, cy);
            diamondPath.lineTo(cx, cy + d);
            diamondPath.lineTo(cx - d, cy);
            diamondPath.close();
            paint.setColor(Color.parseColor("#FFFF6B4A"));
            canvas.drawPath(diamondPath, paint);

            postInvalidateOnAnimation();
        }
    }

    // =========================================================================
    // Bàn thử nghiệm vật lý lò xo & sóng điều hòa tương tác (Spring Wave Field)
    // =========================================================================
    private static class SpringWaveFieldView extends View {
        private static final int NODES = 17;
        private static final int ORBS = 4;

        private final float density;
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ribbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path wavePath = new Path();
        private final Path fillPath = new Path();
        private final Path secWavePath = new Path();

        // Trạng thái lò xo của từng nút sóng
        private final float[] nodeDisp = new float[NODES];
        private final float[] nodeVel = new float[NODES];

        // Các viên bi động năng trượt trên trường lực
        private final float[] orbX = new float[ORBS];
        private final float[] orbY = new float[ORBS];
        private final float[] orbVx = new float[ORBS];
        private final float[] orbVy = new float[ORBS];
        private final int[] orbColors = {
                Color.parseColor("#FFFF6B4A"),
                Color.parseColor("#FF6366F1"),
                Color.parseColor("#FF10B981"),
                Color.parseColor("#FFF59E0B")
        };

        private float touchX = -1f, touchY = -1f, rippleR = 0f, rippleAlpha = 0f;
        private boolean initialized = false;
        private float time = 0f;
        private long lastNanos = 0L;

        SpringWaveFieldView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;

            gridPaint.setColor(Color.parseColor("#18FFFFFF"));
            gridPaint.setStrokeWidth(1f);

            ribbonPaint.setStyle(Paint.Style.STROKE);
            ribbonPaint.setStrokeCap(Paint.Cap.ROUND);
            ribbonPaint.setStrokeWidth(2.4f * density);

            labelPaint.setColor(Color.parseColor("#999EA4B4"));
            labelPaint.setTextSize(10f * density);
            labelPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        }

        private void initOrbs(int w, int h) {
            for (int i = 0; i < ORBS; i++) {
                orbX[i] = w * (0.18f + i * 0.21f);
                orbY[i] = h * 0.45f;
                orbVx[i] = ((i % 2 == 0) ? 1 : -1) * (42f + i * 14f) * density;
                orbVy[i] = (-35f - i * 18f) * density;
            }
            fillPaint.setShader(new LinearGradient(0, h * 0.25f, 0, h,
                    Color.parseColor("#40FF6B4A"),
                    Color.parseColor("#00FF6B4A"),
                    Shader.TileMode.CLAMP));
            initialized = true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float tx = event.getX();
                float ty = event.getY();
                int w = Math.max(1, getWidth());
                int h = Math.max(1, getHeight());
                float baseLine = h * 0.58f;

                touchX = tx;
                touchY = ty;
                if (action == MotionEvent.ACTION_DOWN) {
                    rippleR = 6f * density;
                    rippleAlpha = 0.85f;
                }

                // Tác động lực đàn hồi lên các nút lò xo gần điểm chạm
                for (int i = 0; i < NODES; i++) {
                    float nx = (w * i) / (float) (NODES - 1);
                    float dist = Math.abs(nx - tx);
                    float influence = Math.max(0f, 1f - dist / (110f * density));
                    if (influence > 0f) {
                        float targetOffset = (ty - baseLine) * influence * 0.85f;
                        nodeVel[i] += (targetOffset - nodeDisp[i]) * 14f;
                    }
                }

                // Đẩy các viên bi động năng
                for (int i = 0; i < ORBS; i++) {
                    float dx = orbX[i] - tx;
                    float dy = orbY[i] - ty;
                    float d = (float) Math.hypot(dx, dy);
                    if (d < 120f * density) {
                        float inv = d > 1f ? 1f / d : 0f;
                        orbVx[i] += dx * inv * 180f * density;
                        orbVy[i] = -195f * density;
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
            if (!initialized) initOrbs(w, h);

            long now = System.nanoTime();
            float dt = lastNanos == 0L ? 1f / 60f : (now - lastNanos) / 1_000_000_000f;
            lastNanos = now;
            if (dt > 0.04f) dt = 0.04f;
            time += dt;

            // Lưới chấm kỹ thuật
            float step = 24f * density;
            for (float gx = step; gx < w; gx += step) {
                for (float gy = step; gy < h; gy += step) {
                    canvas.drawCircle(gx, gy, 1f * density, gridPaint);
                }
            }

            // Tiêu đề kỹ thuật góc trên
            canvas.drawText("SPRING WAVE FIELD · CHẠM ĐỂ TƯƠNG TÁC", 14f * density, 19f * density, labelPaint);

            // Giải hệ phương trình vi phân lò xo (Hooke + lan truyền sóng lân cận)
            float stiffness = 68f;
            float damping = 4.2f;
            float spread = 95f;
            for (int i = 0; i < NODES; i++) {
                float ambient = (float) Math.sin(time * 2.8f + i * 0.45f) * 9f * density;
                float force = -stiffness * (nodeDisp[i] - ambient) - damping * nodeVel[i];
                if (i > 0) force += spread * (nodeDisp[i - 1] - nodeDisp[i]);
                if (i < NODES - 1) force += spread * (nodeDisp[i + 1] - nodeDisp[i]);
                nodeVel[i] += force * dt;
            }
            for (int i = 0; i < NODES; i++) {
                nodeDisp[i] += nodeVel[i] * dt;
            }

            float baseLine = h * 0.60f;
            wavePath.reset();
            fillPath.reset();
            secWavePath.reset();

            float prevX = 0f, prevY = baseLine + nodeDisp[0];
            float prevSecY = baseLine - nodeDisp[0] * 0.55f + (float) Math.cos(time * 2.1f) * 6f * density;
            wavePath.moveTo(prevX, prevY);
            fillPath.moveTo(0f, h);
            fillPath.lineTo(prevX, prevY);
            secWavePath.moveTo(prevX, prevSecY);

            for (int i = 1; i < NODES; i++) {
                float cx = (w * i) / (float) (NODES - 1);
                float cy = baseLine + nodeDisp[i];
                float midX = (prevX + cx) * 0.5f;
                float midY = (prevY + cy) * 0.5f;
                wavePath.quadTo(prevX, prevY, midX, midY);
                fillPath.quadTo(prevX, prevY, midX, midY);

                float secY = baseLine - nodeDisp[i] * 0.55f
                        + (float) Math.cos(time * 2.1f + i * 0.5f) * 6f * density;
                secWavePath.quadTo(prevX, prevSecY, (prevX + cx) * 0.5f, (prevSecY + secY) * 0.5f);

                prevX = cx;
                prevY = cy;
                prevSecY = secY;
            }
            wavePath.lineTo(prevX, prevY);
            fillPath.lineTo(prevX, prevY);
            fillPath.lineTo(w, h);
            fillPath.close();
            secWavePath.lineTo(prevX, prevSecY);

            // Vẽ sóng phụ Indigo phía sau
            ribbonPaint.setColor(Color.parseColor("#886366F1"));
            ribbonPaint.setStrokeWidth(1.6f * density);
            canvas.drawPath(secWavePath, ribbonPaint);

            // Vẽ vùng sáng dưới đường cong chính
            canvas.drawPath(fillPath, fillPaint);

            // Vẽ đường cong đàn hồi chính
            ribbonPaint.setColor(Color.parseColor("#FFFF6B4A"));
            ribbonPaint.setStrokeWidth(2.4f * density);
            canvas.drawPath(wavePath, ribbonPaint);

            // Vẽ các nút neo trên dây sóng
            nodePaint.setStyle(Paint.Style.FILL);
            for (int i = 1; i < NODES - 1; i += 2) {
                float nx = (w * i) / (float) (NODES - 1);
                float ny = baseLine + nodeDisp[i];
                nodePaint.setColor(Color.parseColor("#33FFFFFF"));
                canvas.drawLine(nx, ny, nx, h, nodePaint);
                nodePaint.setColor(Color.WHITE);
                canvas.drawCircle(nx, ny, 2.2f * density, nodePaint);
            }

            // Cập nhật và vẽ 4 viên bi tương tác nảy trên mặt sóng
            float gravity = 320f * density;
            for (int i = 0; i < ORBS; i++) {
                orbVy[i] += gravity * dt;
                orbX[i] += orbVx[i] * dt;
                orbY[i] += orbVy[i] * dt;

                float r = (8.5f + (i % 2) * 2f) * density;
                if (orbX[i] < r + 8f * density) {
                    orbX[i] = r + 8f * density;
                    orbVx[i] = Math.abs(orbVx[i]);
                } else if (orbX[i] > w - r - 8f * density) {
                    orbX[i] = w - r - 8f * density;
                    orbVx[i] = -Math.abs(orbVx[i]);
                }

                // Nội suy độ cao mặt sóng tại vị trí viên bi
                float normX = Math.max(0f, Math.min(1f, orbX[i] / w)) * (NODES - 1);
                int idx = Math.min(NODES - 2, (int) normX);
                float frac = normX - idx;
                float waveY = baseLine + nodeDisp[idx] * (1f - frac) + nodeDisp[idx + 1] * frac;

                if (orbY[i] + r > waveY) {
                    orbY[i] = waveY - r;
                    orbVy[i] = -Math.max(Math.abs(orbVy[i]) * 0.78f, 115f * density);
                    nodeVel[idx] += 85f * density;
                }
                if (orbY[i] < r + 22f * density) {
                    orbY[i] = r + 22f * density;
                    orbVy[i] = Math.abs(orbVy[i]) * 0.7f;
                }

                // Vẽ viên bi với điểm phản quang tinh tế
                nodePaint.setColor(orbColors[i]);
                canvas.drawCircle(orbX[i], orbY[i], r, nodePaint);
                nodePaint.setColor(Color.parseColor("#66FFFFFF"));
                canvas.drawCircle(orbX[i] - r * 0.3f, orbY[i] - r * 0.3f, r * 0.28f, nodePaint);
            }

            // Vòng sóng lan tỏa khi chạm
            if (rippleAlpha > 0.02f) {
                nodePaint.setStyle(Paint.Style.STROKE);
                nodePaint.setStrokeWidth(1.5f * density);
                nodePaint.setColor(Color.WHITE);
                nodePaint.setAlpha((int) (rippleAlpha * 160));
                canvas.drawCircle(touchX, touchY, rippleR, nodePaint);
                rippleR += 180f * density * dt;
                rippleAlpha *= 0.90f;
            }

            postInvalidateOnAnimation();
        }
    }

    // =========================================================================
    // Cửa sổ Viewport 60 FPS thu nhỏ bên trong mỗi Card
    // =========================================================================
    private static class CardMiniPreviewView extends View {
        private final int type;
        private final float density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
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

            // Đường trục tâm mảnh kiểu Viewport kỹ thuật
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(Color.parseColor("#18FFFFFF"));
            canvas.drawLine(0, cy, w, cy, paint);
            canvas.drawLine(cx, 0, cx, h, paint);

            if (type == 0) {
                // Module 01: Keyframe Motion với dư ảnh Onion-Skinning
                for (int g = 3; g >= 0; g--) {
                    float tGhost = time * 2.6f - g * 0.16f;
                    float gx = cx + (float) Math.sin(tGhost) * 18f * density;
                    float gy = cy - (float) Math.abs(Math.cos(tGhost)) * 12f * density + 4f * density;
                    float rot = (float) Math.sin(tGhost) * 32f;
                    float sz = (g == 0 ? 8.5f : (7.5f - g * 1.1f)) * density;

                    canvas.save();
                    canvas.translate(gx, gy);
                    canvas.rotate(rot);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.parseColor("#FFFF6B4A"));
                    paint.setAlpha(g == 0 ? 255 : (95 - g * 24));
                    canvas.drawRoundRect(-sz, -sz, sz, sz, 3.5f * density, 3.5f * density, paint);
                    canvas.restore();
                }

                // Thanh Keyframe nhỏ phía dưới
                float barY = h - 10f * density;
                paint.setColor(Color.parseColor("#33FFFFFF"));
                paint.setStrokeWidth(1.5f * density);
                canvas.drawLine(12f * density, barY, w - 12f * density, barY, paint);
                float headX = cx + (float) Math.sin(time * 2.6f) * (w * 0.5f - 14f * density);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(headX, barY, 2.5f * density, paint);

            } else if (type == 1) {
                // Module 02: Đường cong Bezier Lissajous & Đầu bút Vector
                path.reset();
                int steps = 34;
                for (int i = 0; i <= steps; i++) {
                    float t = time * 2.2f - i * 0.065f;
                    float px = cx + (float) Math.sin(t) * 20f * density;
                    float py = cy + (float) Math.sin(t * 2f) * 13f * density;
                    if (i == 0) path.moveTo(px, py);
                    else path.lineTo(px, py);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(2.8f * density);
                paint.setColor(Color.parseColor("#FF6366F1"));
                canvas.drawPath(path, paint);

                float hx = cx + (float) Math.sin(time * 2.2f) * 20f * density;
                float hy = cy + (float) Math.sin(time * 4.4f) * 13f * density;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#FFFF6B4A"));
                canvas.drawCircle(hx, hy, 4.2f * density, paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(hx, hy, 1.8f * density, paint);

            } else {
                // Module 03: Hệ thống hạt bung nở theo trọng lực kèm vòng Shockwave
                float cycle = (time * 0.85f) % 1f;
                float maxR = 25f * density;
                float curR = cycle * maxR;
                int alpha = (int) ((1f - cycle) * 255);

                // Vòng sóng xung kích
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.2f * density);
                paint.setColor(Color.parseColor("#FF10B981"));
                paint.setAlpha((int) (alpha * 0.55f));
                canvas.drawCircle(cx, cy - 3f * density, curR * 0.85f, paint);

                // Các tia hạt chịu trọng lực cong xuống
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(2.0f * density);
                for (int i = 0; i < 10; i++) {
                    double angle = i * Math.PI / 5 + 0.25;
                    float r0 = Math.max(0f, curR - 5f * density);
                    float gravDrop = cycle * cycle * 9f * density;
                    float x0 = cx + (float) Math.cos(angle) * r0;
                    float y0 = cy - 3f * density + (float) Math.sin(angle) * r0 + gravDrop * 0.7f;
                    float x1 = cx + (float) Math.cos(angle) * curR;
                    float y1 = cy - 3f * density + (float) Math.sin(angle) * curR + gravDrop;

                    paint.setColor(i % 2 == 0 ? Color.parseColor("#FF10B981") : Color.parseColor("#FFF59E0B"));
                    paint.setAlpha(alpha);
                    canvas.drawLine(x0, y0, x1, y1, paint);
                }
            }

            postInvalidateOnAnimation();
        }
    }
}
