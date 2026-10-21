package com.example.animationcanvas.particle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Custom View mô phỏng hệ thống hạt & pháo hoa vật lý (Particle Fireworks Lab):
 * - Vòng lặp thời gian thực 60 FPS (postInvalidateOnAnimation) độc lập tốc độ khung hình nhờ dt.
 * - Vật lý: Vận tốc (vx, vy), Trọng lực (gravity), Lực cản không khí (drag), Độ mờ theo tuổi thọ (fade alpha).
 * - 6 kiểu nổ hình học: Ngẫu nhiên (Mix), Cầu hoa cúc (Sphere), Vòng kép (Double Ring),
 *   Trái tim (Heart Parametric), Ngôi sao 5 cánh (Star), Xoắn ốc thiên hà (Spiral Galaxy).
 * - Hiệu ứng Sóng xung kích (Shockwave Ring), Ánh chớp nổ (Flash Glow) và Bầu trời đêm thành phố.
 */
public class ParticleView extends View {

    public interface OnFirstTouchListener {
        void onFirstTouch();
    }

    public static final int PATTERN_MIX = 0;
    public static final int PATTERN_SPHERE = 1;
    public static final int PATTERN_RING = 2;
    public static final int PATTERN_HEART = 3;
    public static final int PATTERN_STAR = 4;
    public static final int PATTERN_SPIRAL = 5;

    private static final int MAX_PARTICLES = 3500;
    private static final int STAR_COUNT = 95;

    private static class Shockwave {
        float x, y, radius, maxRadius, alpha;
        int color;
    }

    private final List<Particle> particles = new ArrayList<>();
    private final List<Particle> pending = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final Random random = new Random();
    private final float density;

    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cityPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Sao nền
    private final float[] starX = new float[STAR_COUNT];
    private final float[] starY = new float[STAR_COUNT];
    private final float[] starR = new float[STAR_COUNT];
    private final float[] starPhase = new float[STAR_COUNT];

    // Đường chân trời thành phố (tỉ lệ chiều rộng & chiều cao toà nhà)
    private static final float[] BUILDING_HEIGHTS = {
            0.05f, 0.09f, 0.06f, 0.12f, 0.07f, 0.14f, 0.08f, 0.11f,
            0.06f, 0.15f, 0.09f, 0.07f, 0.13f, 0.08f, 0.10f, 0.05f
    };

    private float gravityDp = 500f;
    private int selectedPattern = PATTERN_MIX;
    private boolean autoFireworks = false;
    private float autoTimer = 0f;
    private float trailHue = 0f;
    private float time = 0f;

    private long lastFrameNanos = 0L;
    private int frameCount = 0;
    private float fpsTimer = 0f;
    private int fps = 60;

    private boolean touchedOnce = false;
    private OnFirstTouchListener firstTouchListener;

    public ParticleView(Context context) {
        this(context, null);
    }

    public ParticleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        particlePaint.setStyle(Paint.Style.STROKE);
        particlePaint.setStrokeCap(Paint.Cap.ROUND);
        particlePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));

        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));

        starPaint.setColor(Color.WHITE);
        cityPaint.setColor(Color.parseColor("#FF0B0D14"));

        hudBgPaint.setColor(Color.parseColor("#B30A0C1D"));
        hudPaint.setColor(Color.parseColor("#999EA4B4"));
        hudPaint.setTextSize(10.5f * density);
        hudPaint.setTypeface(android.graphics.Typeface.MONOSPACE);

        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = random.nextFloat();
            starY[i] = random.nextFloat();
            starR[i] = (0.5f + random.nextFloat() * 1.3f) * density;
            starPhase[i] = random.nextFloat() * 6.283f;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bgPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{
                        Color.parseColor("#0A0C12"),
                        Color.parseColor("#111521"),
                        Color.parseColor("#171C2C"),
                        Color.parseColor("#1D2235")
                },
                new float[]{0f, 0.45f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        if (oldw == 0 && w > 0) {
            postDelayed(this::launchRocket, 180);
        }
    }

    // ------------------------------------------------------------------
    // Vòng lặp Game Loop trong onDraw()
    // ------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 1f / 60f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        if (dt > 0.05f) dt = 0.05f;

        update(dt);
        drawBackgroundAndSkyline(canvas);
        drawShockwaves(canvas);
        drawParticles(canvas);
        drawHud(canvas, dt);

        postInvalidateOnAnimation();
    }

    private void update(float dt) {
        time += dt;
        float gravityPx = gravityDp * density;
        float rocketGravity = Math.max(gravityPx, 260f * density);

        if (autoFireworks) {
            autoTimer -= dt;
            if (autoTimer <= 0f) {
                launchRocket();
                autoTimer = 0.42f + random.nextFloat() * 0.65f;
            }
        }

        int h = getHeight();
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);

            if (p.type == Particle.TYPE_ROCKET) {
                p.update(dt, rocketGravity);
                spawnRocketTrail(p);
                if (p.vy >= 0f || p.isDead()) {
                    explode(p.x, p.y, pending);
                    removeAt(i);
                    continue;
                }
            } else {
                p.update(dt, gravityPx);
            }

            if (p.isDead() || p.y > h + 50 * density) {
                removeAt(i);
            }
        }

        for (int i = shockwaves.size() - 1; i >= 0; i--) {
            Shockwave sw = shockwaves.get(i);
            sw.radius += 290f * density * dt;
            sw.alpha = Math.max(0f, 1f - sw.radius / sw.maxRadius);
            if (sw.radius >= sw.maxRadius) {
                shockwaves.remove(i);
            }
        }

        if (!pending.isEmpty()) {
            addAllCapped(pending);
            pending.clear();
        }
    }

    private void removeAt(int i) {
        int last = particles.size() - 1;
        if (i != last) particles.set(i, particles.get(last));
        particles.remove(last);
    }

    private void drawBackgroundAndSkyline(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, bgPaint);

        // Sao nhấp nháy
        for (int i = 0; i < STAR_COUNT; i++) {
            float twinkle = 0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(time * 2.2f + starPhase[i]));
            starPaint.setAlpha((int) (twinkle * 190));
            canvas.drawCircle(starX[i] * w, starY[i] * h * 0.78f, starR[i], starPaint);
        }

        // Bóng dáng thành phố về đêm phía chân trời
        float bw = (float) w / BUILDING_HEIGHTS.length;
        for (int i = 0; i < BUILDING_HEIGHTS.length; i++) {
            float bh = BUILDING_HEIGHTS[i] * h;
            float left = i * bw;
            float top = h - bh;
            canvas.drawRect(left, top, left + bw + 1f, h, cityPaint);
        }
    }

    private void drawShockwaves(Canvas canvas) {
        for (int i = 0, n = shockwaves.size(); i < n; i++) {
            Shockwave sw = shockwaves.get(i);
            wavePaint.setColor(sw.color);
            wavePaint.setAlpha((int) (sw.alpha * 140));
            wavePaint.setStrokeWidth((1.5f + 3f * sw.alpha) * density);
            canvas.drawCircle(sw.x, sw.y, sw.radius, wavePaint);

            // Ánh chớp tâm nổ khi mới bùng phát
            if (sw.alpha > 0.6f) {
                glowPaint.setColor(sw.color);
                glowPaint.setAlpha((int) ((sw.alpha - 0.6f) * 2.5f * 90));
                canvas.drawCircle(sw.x, sw.y, sw.radius * 0.65f, glowPaint);
            }
        }
    }

    private void drawParticles(Canvas canvas) {
        for (int i = 0, n = particles.size(); i < n; i++) {
            Particle p = particles.get(i);
            float alpha = p.getAlpha();
            int a = (int) (alpha * 255);

            if (p.type != Particle.TYPE_TRAIL) {
                glowPaint.setColor(p.color);
                glowPaint.setAlpha((int) (a * 0.14f));
                canvas.drawCircle(p.x, p.y, p.radius * 2.6f, glowPaint);
            }

            particlePaint.setColor(p.color);
            particlePaint.setAlpha(a);
            particlePaint.setStrokeWidth(p.radius * 2f * (0.4f + 0.6f * alpha));
            canvas.drawLine(p.prevX, p.prevY, p.x, p.y, particlePaint);
        }
    }

    private void drawHud(Canvas canvas, float dt) {
        frameCount++;
        fpsTimer += dt;
        if (fpsTimer >= 0.4f) {
            fps = Math.round(frameCount / fpsTimer);
            frameCount = 0;
            fpsTimer = 0f;
        }
        String text = String.format(Locale.US, "Hạt: %d   ·   FPS: %d", particles.size(), fps);
        canvas.drawText(text, 18 * density, 88 * density, hudPaint);
    }

    // ------------------------------------------------------------------
    // Sinh hạt & 6 kiểu nổ hình học
    // ------------------------------------------------------------------

    private void explode(float x, float y, List<Particle> out) {
        float baseHue = random.nextFloat() * 360f;
        int pattern = selectedPattern == PATTERN_MIX
                ? (1 + random.nextInt(5))
                : selectedPattern;
        int count = 110 + random.nextInt(50);

        // Tạo sóng xung kích (Shockwave Ring)
        if (shockwaves.size() < 12) {
            Shockwave sw = new Shockwave();
            sw.x = x;
            sw.y = y;
            sw.radius = 6f * density;
            sw.maxRadius = (95f + random.nextFloat() * 45f) * density;
            sw.alpha = 1f;
            sw.color = Color.HSVToColor(new float[]{baseHue, 0.65f, 1f});
            shockwaves.add(sw);
        }

        for (int i = 0; i < count; i++) {
            float vx, vy;
            float t = (float) (2 * Math.PI * i / count);

            switch (pattern) {
                case PATTERN_RING: { // Vòng kép 2 màu đồng tâm
                    float speed = (i % 2 == 0 ? 255f : 145f) * density;
                    vx = (float) Math.cos(t) * speed;
                    vy = (float) Math.sin(t) * speed;
                    break;
                }
                case PATTERN_HEART: { // Phương trình tham số Trái tim
                    float hx = (float) (16 * Math.pow(Math.sin(t), 3));
                    float hy = -(float) (13 * Math.cos(t) - 5 * Math.cos(2 * t)
                            - 2 * Math.cos(3 * t) - Math.cos(4 * t));
                    float scale = (13.5f + random.nextFloat() * 2.2f) * density;
                    vx = hx * scale;
                    vy = hy * scale;
                    break;
                }
                case PATTERN_STAR: { // Ngôi sao 5 cánh
                    int spikes = 5;
                    double wave = Math.cos(spikes * t);
                    float rFactor = 0.45f + 0.55f * (float) Math.pow((wave + 1.0) / 2.0, 1.7);
                    float speed = (280f * rFactor + random.nextFloat() * 18f) * density;
                    vx = (float) Math.cos(t - Math.PI / 2) * speed;
                    vy = (float) Math.sin(t - Math.PI / 2) * speed;
                    break;
                }
                case PATTERN_SPIRAL: { // Xoắn ốc thiên hà 3 nhánh
                    float armAngle = (i % 3) * (float) (2 * Math.PI / 3) + (i / (float) count) * 4.5f;
                    float speed = (45f + (i / (float) count) * 240f) * density;
                    vx = (float) Math.cos(armAngle) * speed;
                    vy = (float) Math.sin(armAngle) * speed;
                    break;
                }
                case PATTERN_SPHERE:
                default: { // Cầu hoa cúc (Peony Sphere)
                    double angle = random.nextDouble() * 2 * Math.PI;
                    float speed = (40f + random.nextFloat() * 270f) * density;
                    vx = (float) Math.cos(angle) * speed;
                    vy = (float) Math.sin(angle) * speed;
                    break;
                }
            }

            float hue;
            if (pattern == PATTERN_HEART) {
                hue = (335f + random.nextFloat() * 35f) % 360f; // Đỏ hồng lãng mạn
            } else if (pattern == PATTERN_RING && i % 2 == 1) {
                hue = (baseHue + 160f) % 360f;
            } else {
                hue = (baseHue + random.nextFloat() * 46f - 23f + 360f) % 360f;
            }

            int color = random.nextInt(9) == 0 ? Color.WHITE
                    : Color.HSVToColor(new float[]{hue, 0.6f + random.nextFloat() * 0.4f, 1f});

            out.add(new Particle(Particle.TYPE_SPARK, x, y,
                    vx, vy,
                    1.15f + random.nextFloat() * 1.05f,
                    (1.4f + random.nextFloat() * 1.6f) * density,
                    color, 0.32f, 0.52f));
        }
    }

    /** Bắn 1 quả pháo từ đáy màn hình lên độ cao ngẫu nhiên. */
    public void launchRocket() {
        if (getWidth() == 0) return;
        float x = getWidth() * (0.14f + random.nextFloat() * 0.72f);
        float y = getHeight();
        float targetHeight = getHeight() * (0.46f + random.nextFloat() * 0.30f);
        float g = Math.max(gravityDp * density, 260f * density);
        float vy = -(float) Math.sqrt(2f * g * targetHeight);
        float vx = (random.nextFloat() - 0.5f) * 65f * density;

        Particle rocket = new Particle(Particle.TYPE_ROCKET, x, y, vx, vy,
                4f, 2.6f * density, Color.parseColor("#FFE3A3"), 1f, 1f);
        if (particles.size() < MAX_PARTICLES) particles.add(rocket);
    }

    /** Đại tiệc pháo hoa: Bắn đồng loạt 5 quả pháo theo đội hình rẻ quạt. */
    public void launchSalvo() {
        if (getWidth() == 0) return;
        int count = 5;
        float g = Math.max(gravityDp * density, 260f * density);
        for (int i = 0; i < count; i++) {
            float ratio = (i + 0.5f) / count;
            float x = getWidth() * (0.15f + ratio * 0.70f);
            float y = getHeight();
            float targetHeight = getHeight() * (0.48f + (i % 2 == 0 ? 0.18f : 0.08f) + random.nextFloat() * 0.1f);
            float vy = -(float) Math.sqrt(2f * g * targetHeight);
            float vx = (ratio - 0.5f) * 90f * density;
            Particle rocket = new Particle(Particle.TYPE_ROCKET, x, y, vx, vy,
                    4f, 2.7f * density, Color.parseColor("#FFF08A"), 1f, 1f);
            if (particles.size() < MAX_PARTICLES) particles.add(rocket);
        }
    }

    private void spawnRocketTrail(Particle rocket) {
        for (int k = 0; k < 2; k++) {
            pending.add(new Particle(Particle.TYPE_TRAIL, rocket.x, rocket.y,
                    (random.nextFloat() - 0.5f) * 42f * density,
                    (random.nextFloat() * 42f + 20f) * density,
                    0.35f + random.nextFloat() * 0.25f,
                    (0.8f + random.nextFloat()) * density,
                    Color.parseColor("#FFB547"), 0.2f, 0.3f));
        }
    }

    private void spawnTouchTrail(float x, float y) {
        trailHue = (trailHue + 4.5f) % 360f;
        int color = Color.HSVToColor(new float[]{trailHue, 0.75f, 1f});
        for (int k = 0; k < 5; k++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            float speed = random.nextFloat() * 90f * density;
            pending.add(new Particle(Particle.TYPE_TRAIL, x, y,
                    (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed,
                    0.5f + random.nextFloat() * 0.5f,
                    (1.2f + random.nextFloat() * 1.6f) * density,
                    color, 0.25f, 0.38f));
        }
        addAllCapped(pending);
        pending.clear();
    }

    private void addAllCapped(List<Particle> list) {
        int room = MAX_PARTICLES - particles.size();
        if (room <= 0) return;
        if (list.size() <= room) {
            particles.addAll(list);
        } else {
            particles.addAll(list.subList(0, room));
        }
    }

    // ------------------------------------------------------------------
    // Cảm ứng đa điểm & tương tác
    // ------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!touchedOnce) {
                    touchedOnce = true;
                    if (firstTouchListener != null) firstTouchListener.onFirstTouch();
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                explode(x, y, pending);
                addAllCapped(pending);
                pending.clear();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                int idx = event.getActionIndex();
                explode(event.getX(idx), event.getY(idx), pending);
                addAllCapped(pending);
                pending.clear();
                return true;
            case MotionEvent.ACTION_MOVE:
                spawnTouchTrail(x, y);
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // ------------------------------------------------------------------
    // API công khai
    // ------------------------------------------------------------------

    public void setGravityDp(float gravityDp) {
        this.gravityDp = gravityDp;
    }

    public void setPattern(int pattern) {
        this.selectedPattern = pattern;
    }

    public void setAutoFireworks(boolean enabled) {
        this.autoFireworks = enabled;
        this.autoTimer = 0f;
    }

    public void clearParticles() {
        particles.clear();
        pending.clear();
        shockwaves.clear();
    }

    public void setOnFirstTouchListener(OnFirstTouchListener listener) {
        this.firstTouchListener = listener;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        lastFrameNanos = 0L;
        if (visibility == VISIBLE) postInvalidateOnAnimation();
    }
}
