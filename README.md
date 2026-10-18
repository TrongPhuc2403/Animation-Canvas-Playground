# Animation & Canvas Playground (Pro Edition)

Ứng dụng minh hoạ chuyên sâu cho đề tài **Đề tài 04 – "Learn about Animation & Canvas and build an illustrated application"**  
*(Môn học: Phát triển ứng dụng di động – Tiểu luận Giữa kỳ)*.

---

## 👥 Phân công Nhóm

| Thành viên | Vai trò & Nhiệm vụ |
|---|---|
| **Hoàn** | Nghiên cứu Lý thuyết Android Animation & Android Canvas, thiết kế Mind Map |
| **Long** | Kiến trúc & Xây dựng toàn bộ ứng dụng minh hoạ **Animation & Canvas Playground** |
| **Phúc** | Tổng hợp Báo cáo (Report), thiết kế Slide thuyết trình & Quay Video Demo ($\le 2$ phút) |

---

## ✨ Điểm nhấn Kiến trúc & Tính năng

### 0. Màn hình chính – Interactive Constellation Dashboard (`MainActivity.java`)
- **Custom View nền thời gian thực (`InteractiveHeroView`)**:
  - Vẽ hào quang cực quang (**RadialGradient Aurora**) chuyển động mượt mà.
  - Hệ thống 34 hạt mạng lưới sao (**Constellation Network**) tự động nối dây phát sáng khi tiến lại gần nhau.
  - Phản hồi sóng xung kích (**Touch Ripple**) tại đúng tọa độ chạm trên toàn màn hình.
- **Hệ thống Animation đa lớp**:
  - Logo nhịp thở (`ObjectAnimator` + `PropertyValuesHolder`), vành sáng co giãn ngược pha, icon trung tâm xoay vô hạn (`LinearInterpolator`).
  - Các Card xuất hiện so le (**Staggered Entrance** với `OvershootInterpolator`) kèm các icon động minh hoạ trực tiếp cho từng Module.

### 1. Module 01: Animation Studio (`animation/AnimationActivity.java`)
- **8 Chế độ Property Animation**:
  1. **Move (Translate)**: Dịch chuyển theo trục ngang kèm bóng đổ đồng bộ.
  2. **Rotate 3D**: Kết hợp xoay 2D (`rotation`) và lật không gian 3D (`rotationY` với `setCameraDistance`).
  3. **Scale**: Phóng đại & thu nhỏ đàn hồi đồng thời `scaleX`, `scaleY` bằng `PropertyValuesHolder`.
  4. **Fade (Alpha)**: Biến thiên độ trong suốt `1.0f -> 0.05f -> 1.0f`.
  5. **Bounce (Gravity + Squash & Stretch)**: Rơi từ đỉnh sân khấu với `BounceInterpolator` kết hợp biến dạng nén dẹt khi chạm mặt đất.
  6. **Orbit**: Chuyển động quỹ đạo tròn/elip bằng `ValueAnimator` + hàm lượng giác $(\sin\theta, \cos\theta)$.
  7. **Elastic Wave**: Dao động sóng hình sin đa pha kết hợp nghiêng góc.
  8. **Combo Set**: Phối hợp phức hợp `AnimatorSet` (`playTogether` + `playSequentially`).
- **Đồ thị hàm số Interpolator thời gian thực (`InterpolatorGraphView`)**:
  - Vẽ trực tiếp đường cong toán học $y = f(t)$ của 7 bộ nội suy (`AccelerateDecelerate`, `Linear`, `Accelerate`, `Decelerate`, `Overshoot`, `AnticipateOvershoot`, `Bounce`) kèm điểm sáng chạy dọc theo đồ thị ở tốc độ 60 FPS.
- **Sân khấu toạ độ & Vệt quỹ đạo (`StageBackdropView`)**:
  - Lưới trục $Oxy$, vẽ vệt đuôi chuyển động (**Motion Trail**) và cho phép **chạm bất kỳ đâu trên sân khấu** để di chuyển vật thể tới điểm chạm.
- **Đổi hình dáng vật thể**: Chuyển đổi tức thì giữa `🔮 Orb`, `🚀 Rocket`, `⭐ Star`, `💎 Cube` và bật/tắt chế độ **Lặp vô hạn (`INFINITE`)**.

### 2. Module 02: Canvas Drawing Lab (`drawing/DrawingView.java`, `drawing/DrawingActivity.java`)
- **6 Công cụ vẽ trên Custom View (`onDraw`, `onTouchEvent`, `invalidate`)**:
  - `✏️ Bút (Brush)`: Nội suy đường cong Bezier bậc 2 (`Path.quadTo()`) cho nét vẽ mượt.
  - `📏 Đường (Line)`, `⬜ Khung (RoundRect)`, `⭕ Tròn (Circle)` kèm thước dẫn hướng trực quan khi kéo.
  - `⭐ Sao (Star)`: Dựng hình ngôi sao 5 cánh bằng thuật toán lượng giác trên `Path`.
  - `🧽 Tẩy (Eraser)`: Xoá nét mượt mà.
- **4 Chế độ Studio nâng cao**:
  - **✨ Bút Neon**: Vẽ 2 lớp (`glowPaint` mờ rộng + `corePaint` sắc nét) tạo hiệu ứng đèn Neon rực rỡ.
  - **🪞 Đối xứng 4x (Mandala Mirror)**: Biến đổi ma trận `Matrix.postScale` đối xứng qua 2 trục tâm giúp vẽ hoa văn kính vạn hoa 4 góc thời gian thực.
  - **🎨 Tô đặc (Fill / Stroke)**: Chuyển đổi `Paint.Style.FILL_AND_STROKE` và `STROKE`.
  - **🌙 Bảng đêm / ☀️ Giấy sáng**: Chuyển đổi nền lưới kỹ thuật tối và giấy chấm bi sáng.
- **Kết hợp Canvas + Animation (`🎬 Replay Path Animation`)**:
  - Sử dụng `PathMeasure.getSegment()` kết hợp `ValueAnimator` để tái hiện lại toàn bộ quá trình vẽ từng nét từ đầu đến cuối.

### 3. Module 03: Particle Fireworks Lab (`particle/ParticleView.java`, `particle/Particle.java`)
- **6 Kiểu nổ pháo hoa hình học**:
  - `🎲 Ngẫu nhiên (Mix)`, `🎆 Cầu hoa cúc (Peony Sphere)`, `🪐 Vòng kép (Double Ring)`, `💖 Trái tim (Heart Parametric)`, `⭐ Ngôi sao 5 cánh (Star)`, `🌀 Xoắn ốc thiên hà (Spiral Galaxy)`.
- **Mô phỏng Vật lý 60 FPS (`postInvalidateOnAnimation`)**:
  - Tính toán bước thời gian thực `dt`, vận tốc $(v_x, v_y)$, gia tốc trọng trường $g$ (tuỳ chỉnh từ $0$ đến $1500\text{ dp/s}^2$), lực cản không khí $v \leftarrow v \cdot \text{drag}^{dt}$, độ mờ giảm dần theo tuổi thọ ($\alpha = \text{life}/\text{maxLife}$).
  - Hiệu ứng **Sóng xung kích (`Shockwave Ring`)**, hoà trộn ánh sáng cộng tính (`PorterDuff.Mode.ADD`), bầu trời sao nhấp nháy, đường chân trời thành phố và chế độ **🎇 Đại tiệc (Salvo x5)**.

---

## 🛠️ Hướng dẫn Build & Chạy

```bash
./gradlew assembleDebug
# File APK xuất ra tại: app/build/outputs/apk/debug/app-debug.apk
```
