package com.example.animationcanvas.drawing;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Custom View cho phép vẽ lên Canvas kết hợp hiệu ứng chuyển động:
 * - Vẽ tay mượt bằng Path.quadTo(), vẽ hình cơ bản (Đường, Khung, Tròn, Sao).
 * - Khi kéo bút sẽ phát sinh các hạt mực nhỏ (InkDrop) bung nhẹ quanh đầu bút.
 * - Hỗ trợ chế độ Nét phát sáng, Đối xứng gương 4 góc (Matrix), Tô đặc và Phát lại nét vẽ (PathMeasure).
 */
public class DrawingView extends View {

    public enum Tool { BRUSH, LINE, RECT, CIRCLE, STAR, ERASER }

    public interface OnHistoryChangeListener {
        void onHistoryChanged(boolean canUndo, boolean canRedo);
    }

    private static class Stroke {
        final Path path;
        final Paint paint;
        final Paint glowPaint;

        Stroke(Path path, Paint paint, @Nullable Paint glowPaint) {
            this.path = path;
            this.paint = paint;
            this.glowPaint = glowPaint;
        }
    }

    private static class InkDrop {
        float x, y, vx, vy, radius, alpha;
        int color;
    }

    private static final float TOUCH_TOLERANCE = 3f;

    private final float density;
    private final Random random = new Random();
    private final List<Stroke> strokes = new ArrayList<>();
    private final List<Stroke> redoStack = new ArrayList<>();
    private final List<InkDrop> inkDrops = new ArrayList<>();

    private Path currentPath;
    private Paint currentPaint;
    private Paint currentGlowPaint;
    private float startX, startY, lastX, lastY, touchX = -1f, touchY = -1f;
    private boolean moved;
    private boolean isTouching = false;

    private Tool tool = Tool.BRUSH;
    private int color = Color.parseColor("#4F46E5");
    private float strokeWidth;
    private boolean neonMode = false;
    private boolean mirrorMode = false;
    private boolean fillShapes = false;
    private boolean darkCanvas = false;

    private int paperColor = Color.parseColor("#FDFCFF");

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mirrorAxisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mirrorTempPath = new Path();
    private final Matrix mirrorMatrix = new Matrix();

    private ValueAnimator replayAnimator;
    private float replayProgress = 1f;
    private boolean isReplaying = false;
    private final Path replaySegmentPath = new Path();
    private final float[] replayPos = new float[2];

    private OnHistoryChangeListener historyListener;

    public DrawingView(Context context) {
        this(context, null);
    }

    public DrawingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        strokeWidth = 8f * density;

        mirrorAxisPaint.setStyle(Paint.Style.STROKE);
        mirrorAxisPaint.setStrokeWidth(1.2f * density);
        mirrorAxisPaint.setColor(Color.parseColor("#666366F1"));
        mirrorAxisPaint.setPathEffect(new DashPathEffect(new float[]{6 * density, 5 * density}, 0));

        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(1.8f * density);
        dropPaint.setStyle(Paint.Style.FILL);

        final float radius = 20f * density;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        canvas.drawColor(paperColor);
        drawDotGrid(canvas, w, h);

        if (mirrorMode) {
            canvas.drawLine(w / 2f, 0, w / 2f, h, mirrorAxisPaint);
            canvas.drawLine(0, h / 2f, w, h / 2f, mirrorAxisPaint);
        }

        if (isReplaying && !strokes.isEmpty()) {
            drawReplayStrokes(canvas);
        } else {
            for (int i = 0, n = strokes.size(); i < n; i++) {
                Stroke s = strokes.get(i);
                if (s.glowPaint != null) canvas.drawPath(s.path, s.glowPaint);
                canvas.drawPath(s.path, s.paint);
            }
        }

        if (currentPath != null && !isReplaying) {
            if (mirrorMode && tool != Tool.ERASER) {
                drawMirroredPath(canvas, currentPath, currentPaint, currentGlowPaint, w, h);
            } else {
                if (currentGlowPaint != null) canvas.drawPath(currentPath, currentGlowPaint);
                canvas.drawPath(currentPath, currentPaint);
            }
        }

        // Vẽ các hạt mực bung nhẹ theo nét cọ
        if (!inkDrops.isEmpty()) {
            for (int i = inkDrops.size() - 1; i >= 0; i--) {
                InkDrop d = inkDrops.get(i);
                d.x += d.vx;
                d.y += d.vy;
                d.alpha -= 0.045f;
                if (d.alpha <= 0f) {
                    inkDrops.remove(i);
                    continue;
                }
                dropPaint.setColor(d.color);
                dropPaint.setAlpha((int) (d.alpha * 190));
                canvas.drawCircle(d.x, d.y, d.radius * d.alpha, dropPaint);
            }
            postInvalidateOnAnimation();
        }

        // Vòng tròn đầu bút khi đang chạm
        if (isTouching && touchX >= 0 && touchY >= 0) {
            cursorPaint.setColor(tool == Tool.ERASER ? Color.GRAY : color);
            cursorPaint.setAlpha(170);
            float r = Math.max(strokeWidth / 2f + 5f * density, 11f * density);
            canvas.drawCircle(touchX, touchY, r, cursorPaint);
        }
    }

    private void drawDotGrid(Canvas canvas, int w, int h) {
        float step = 28f * density;
        gridPaint.setColor(darkCanvas ? Color.parseColor("#18FFFFFF") : Color.parseColor("#1E475569"));
        for (float x = step; x < w; x += step) {
            for (float y = step; y < h; y += step) {
                canvas.drawCircle(x, y, 1.3f * density, gridPaint);
            }
        }
    }

    private void drawReplayStrokes(Canvas canvas) {
        int n = strokes.size();
        float scaled = replayProgress * n;
        int fullCount = (int) Math.floor(scaled);
        float partialFraction = scaled - fullCount;

        for (int i = 0; i < fullCount && i < n; i++) {
            Stroke s = strokes.get(i);
            if (s.glowPaint != null) canvas.drawPath(s.path, s.glowPaint);
            canvas.drawPath(s.path, s.paint);
        }

        if (fullCount < n && partialFraction > 0f) {
            Stroke active = strokes.get(fullCount);
            PathMeasure pm = new PathMeasure(active.path, false);
            replaySegmentPath.reset();
            boolean hasPoint = false;
            do {
                float len = pm.getLength();
                float stopD = len * partialFraction;
                pm.getSegment(0f, stopD, replaySegmentPath, true);
                if (pm.getPosTan(stopD, replayPos, null)) {
                    hasPoint = true;
                }
            } while (pm.nextContour());

            if (active.glowPaint != null) canvas.drawPath(replaySegmentPath, active.glowPaint);
            canvas.drawPath(replaySegmentPath, active.paint);

            if (hasPoint) {
                dropPaint.setColor(active.paint.getColor());
                canvas.drawCircle(replayPos[0], replayPos[1], strokeWidth * 0.7f + 3f * density, dropPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isReplaying) return false;

        float x = event.getX();
        float y = event.getY();
        touchX = x;
        touchY = y;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                isTouching = true;
                startX = lastX = x;
                startY = lastY = y;
                moved = false;
                currentPaint = createCorePaint();
                currentGlowPaint = createGlowPaint();
                currentPath = new Path();
                currentPath.moveTo(x, y);
                spawnInkDrop(x, y);
                break;

            case MotionEvent.ACTION_MOVE:
                if (currentPath == null) return false;
                if (tool == Tool.BRUSH || tool == Tool.ERASER) {
                    float dx = Math.abs(x - lastX);
                    float dy = Math.abs(y - lastY);
                    if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                        currentPath.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f);
                        lastX = x;
                        lastY = y;
                        moved = true;
                        if (tool == Tool.BRUSH) spawnInkDrop(x, y);
                    }
                } else {
                    currentPath.reset();
                    buildShape(currentPath, startX, startY, x, y);
                    lastX = x;
                    lastY = y;
                    moved = true;
                }
                break;

            case MotionEvent.ACTION_UP:
                performClick();
                isTouching = false;
                if (currentPath == null) return false;
                if (tool == Tool.BRUSH || tool == Tool.ERASER) {
                    if (moved) {
                        currentPath.lineTo(x, y);
                    } else {
                        currentPath.lineTo(x + 0.1f, y + 0.1f);
                    }
                    commitCurrent();
                } else if (moved) {
                    currentPath.reset();
                    buildShape(currentPath, startX, startY, x, y);
                    commitCurrent();
                } else {
                    currentPath = null;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                isTouching = false;
                currentPath = null;
                break;

            default:
                return false;
        }

        invalidate();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void spawnInkDrop(float x, float y) {
        if (inkDrops.size() > 35 || tool == Tool.ERASER) return;
        InkDrop d = new InkDrop();
        d.x = x;
        d.y = y;
        double a = random.nextDouble() * Math.PI * 2;
        float sp = (1.2f + random.nextFloat() * 2.5f) * density;
        d.vx = (float) Math.cos(a) * sp;
        d.vy = (float) Math.sin(a) * sp;
        d.radius = (2f + random.nextFloat() * 2.5f) * density;
        d.alpha = 0.85f;
        d.color = color;
        inkDrops.add(d);
    }

    private void buildShape(Path path, float x0, float y0, float x1, float y1) {
        switch (tool) {
            case LINE:
                path.moveTo(x0, y0);
                path.lineTo(x1, y1);
                break;
            case RECT:
                float rCorner = 8f * density;
                path.addRoundRect(Math.min(x0, x1), Math.min(y0, y1),
                        Math.max(x0, x1), Math.max(y0, y1),
                        rCorner, rCorner, Path.Direction.CW);
                break;
            case CIRCLE:
                float r = (float) Math.hypot(x1 - x0, y1 - y0);
                path.addCircle(x0, y0, r, Path.Direction.CW);
                break;
            case STAR:
                float outerR = (float) Math.hypot(x1 - x0, y1 - y0);
                float innerR = outerR * 0.42f;
                int points = 5;
                double startAngle = -Math.PI / 2.0;
                for (int i = 0; i < points * 2; i++) {
                    float rad = (i % 2 == 0) ? outerR : innerR;
                    double angle = startAngle + i * Math.PI / points;
                    float px = x0 + (float) (Math.cos(angle) * rad);
                    float py = y0 + (float) (Math.sin(angle) * rad);
                    if (i == 0) path.moveTo(px, py);
                    else path.lineTo(px, py);
                }
                path.close();
                break;
            default:
                break;
        }
    }

    private void drawMirroredPath(Canvas canvas, Path src, Paint core, @Nullable Paint glow, int w, int h) {
        float cx = w / 2f;
        float cy = h / 2f;
        float[][] scales = {{1f, 1f}, {-1f, 1f}, {1f, -1f}, {-1f, -1f}};
        for (float[] sc : scales) {
            mirrorMatrix.reset();
            mirrorMatrix.postScale(sc[0], sc[1], cx, cy);
            src.transform(mirrorMatrix, mirrorTempPath);
            if (glow != null) canvas.drawPath(mirrorTempPath, glow);
            canvas.drawPath(mirrorTempPath, core);
        }
    }

    private void commitCurrent() {
        if (mirrorMode && tool != Tool.ERASER) {
            Path combined = new Path();
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float[][] scales = {{1f, 1f}, {-1f, 1f}, {1f, -1f}, {-1f, -1f}};
            for (float[] sc : scales) {
                mirrorMatrix.reset();
                mirrorMatrix.postScale(sc[0], sc[1], cx, cy);
                currentPath.transform(mirrorMatrix, mirrorTempPath);
                combined.addPath(mirrorTempPath);
            }
            strokes.add(new Stroke(combined, currentPaint, currentGlowPaint));
        } else {
            strokes.add(new Stroke(currentPath, currentPaint, currentGlowPaint));
        }
        redoStack.clear();
        currentPath = null;
        notifyHistory();
    }

    private Paint createCorePaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean canFill = fillShapes && (tool == Tool.RECT || tool == Tool.CIRCLE || tool == Tool.STAR);
        p.setStyle(canFill ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(tool == Tool.ERASER ? strokeWidth * 2.2f : strokeWidth);
        p.setColor(tool == Tool.ERASER ? paperColor : color);
        if (canFill) p.setAlpha(215);
        return p;
    }

    @Nullable
    private Paint createGlowPaint() {
        if (!neonMode || tool == Tool.ERASER) return null;
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setStrokeJoin(Paint.Join.ROUND);
        glow.setStrokeWidth(strokeWidth * 2.5f);
        glow.setColor(color);
        glow.setAlpha(60);
        return glow;
    }

    public void setTool(Tool tool) {
        this.tool = tool;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public void setStrokeWidth(float widthPx) {
        this.strokeWidth = widthPx;
    }

    public void setNeonMode(boolean enabled) {
        this.neonMode = enabled;
        invalidate();
    }

    public void setMirrorMode(boolean enabled) {
        this.mirrorMode = enabled;
        invalidate();
    }

    public void setFillShapes(boolean enabled) {
        this.fillShapes = enabled;
        invalidate();
    }

    public void setDarkCanvas(boolean dark) {
        this.darkCanvas = dark;
        this.paperColor = dark ? Color.parseColor("#141824") : Color.parseColor("#FDFCFF");
        invalidate();
    }

    public void replayDrawing(@Nullable Runnable onComplete) {
        if (strokes.isEmpty()) return;
        if (replayAnimator != null) replayAnimator.cancel();

        isReplaying = true;
        replayProgress = 0f;
        long totalMs = Math.min(4200L, Math.max(1000L, strokes.size() * 320L));

        replayAnimator = ValueAnimator.ofFloat(0f, 1f);
        replayAnimator.setDuration(totalMs);
        replayAnimator.setInterpolator(new LinearInterpolator());
        replayAnimator.addUpdateListener(anim -> {
            replayProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        replayAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isReplaying = false;
                replayProgress = 1f;
                invalidate();
                if (onComplete != null) onComplete.run();
            }
        });
        replayAnimator.start();
    }

    public boolean canUndo() {
        return !strokes.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (!strokes.isEmpty()) {
            redoStack.add(strokes.remove(strokes.size() - 1));
            invalidate();
            notifyHistory();
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            strokes.add(redoStack.remove(redoStack.size() - 1));
            invalidate();
            notifyHistory();
        }
    }

    public void clear() {
        if (replayAnimator != null) replayAnimator.cancel();
        isReplaying = false;
        strokes.clear();
        redoStack.clear();
        inkDrops.clear();
        currentPath = null;
        invalidate();
        notifyHistory();
    }

    public boolean isEmpty() {
        return strokes.isEmpty();
    }

    public void setOnHistoryChangeListener(OnHistoryChangeListener listener) {
        this.historyListener = listener;
        notifyHistory();
    }

    private void notifyHistory() {
        if (historyListener != null) {
            historyListener.onHistoryChanged(canUndo(), canRedo());
        }
    }
}
