package com.example.animationcanvas.particle;

/**
 * Một hạt (particle) trong hệ thống pháo hoa.
 * Lưu vị trí, vận tốc, thời gian sống và màu sắc; tự cập nhật theo vật lý đơn giản.
 */
public class Particle {

    /** Tia lửa sinh ra khi pháo nổ. */
    public static final int TYPE_SPARK = 0;
    /** Quả pháo bay lên, tới đỉnh thì nổ. */
    public static final int TYPE_ROCKET = 1;
    /** Vệt sáng nhỏ (đuôi pháo / vệt ngón tay). */
    public static final int TYPE_TRAIL = 2;

    public float x, y;        // vị trí (px)
    public float prevX, prevY; // vị trí frame trước (để vẽ vệt)
    public float vx, vy;      // vận tốc (px/giây)
    public float life;        // thời gian sống còn lại (giây)
    public final float maxLife;
    public final float radius;
    public final int color;
    public final int type;
    /** Hệ số cản không khí: phần vận tốc còn giữ lại sau 1 giây (0..1). */
    public final float drag;
    /** Hệ số nhân trọng lực riêng cho từng loại hạt. */
    public final float gravityScale;

    public Particle(int type, float x, float y, float vx, float vy,
                    float life, float radius, int color, float drag, float gravityScale) {
        this.type = type;
        this.x = this.prevX = x;
        this.y = this.prevY = y;
        this.vx = vx;
        this.vy = vy;
        this.life = life;
        this.maxLife = life;
        this.radius = radius;
        this.color = color;
        this.drag = drag;
        this.gravityScale = gravityScale;
    }

    /**
     * Cập nhật trạng thái sau khoảng thời gian dt (giây).
     * Dùng dt để chuyển động không phụ thuộc vào FPS của thiết bị.
     */
    public void update(float dt, float gravity) {
        prevX = x;
        prevY = y;

        // Lực cản: v = v * drag^dt
        if (drag < 1f) {
            float k = (float) Math.pow(drag, dt);
            vx *= k;
            vy *= k;
        }
        // Trọng lực kéo hạt xuống (trục y hướng xuống dưới)
        vy += gravity * gravityScale * dt;

        x += vx * dt;
        y += vy * dt;
        life -= dt;
    }

    /** Độ trong suốt 0..1 giảm dần theo thời gian sống -> hiệu ứng fade. */
    public float getAlpha() {
        float a = life / maxLife;
        return a < 0f ? 0f : (a > 1f ? 1f : a);
    }

    public boolean isDead() {
        return life <= 0f;
    }
}
