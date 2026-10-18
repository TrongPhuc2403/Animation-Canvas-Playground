package com.example.animationcanvas.drawing;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.animationcanvas.R;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình vẽ Canvas: kết nối thanh công cụ, màu sắc, cỡ nét, đối xứng và phát lại với DrawingView.
 */
public class DrawingActivity extends AppCompatActivity {

    private static final int MIN_SIZE_DP = 2;

    private static final int[] PALETTE = {
            Color.parseColor("#4F46E5"), // tím indigo
            Color.parseColor("#EC4899"), // hồng
            Color.parseColor("#EF4444"), // đỏ
            Color.parseColor("#F97316"), // cam
            Color.parseColor("#EAB308"), // vàng
            Color.parseColor("#10B981"), // xanh lá
            Color.parseColor("#06B6D4"), // xanh ngọc
            Color.parseColor("#3B82F6"), // xanh dương
            Color.parseColor("#1E293B"), // đen xám
            Color.parseColor("#8B5A2B"), // nâu
    };

    private DrawingView drawingView;
    private ImageButton btnReplay, btnUndo, btnRedo, btnClear;
    private View sizePreview;
    private final List<View> swatches = new ArrayList<>();
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing);
        density = getResources().getDisplayMetrics().density;

        drawingView = findViewById(R.id.drawingView);
        btnReplay = findViewById(R.id.btnReplay);
        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);
        btnClear = findViewById(R.id.btnClear);
        sizePreview = findViewById(R.id.sizePreview);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        setupTools();
        setupModeChips();
        setupPalette();
        setupSizeSeekBar();
        setupHistoryAndReplayButtons();
    }

    private void setupTools() {
        MaterialButtonToggleGroup group = findViewById(R.id.toolGroup);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) return;
            DrawingView.Tool tool = DrawingView.Tool.BRUSH;
            if (checkedId == R.id.toolLine) tool = DrawingView.Tool.LINE;
            else if (checkedId == R.id.toolRect) tool = DrawingView.Tool.RECT;
            else if (checkedId == R.id.toolCircle) tool = DrawingView.Tool.CIRCLE;
            else if (checkedId == R.id.toolStar) tool = DrawingView.Tool.STAR;
            else if (checkedId == R.id.toolEraser) tool = DrawingView.Tool.ERASER;
            drawingView.setTool(tool);
            updateSizePreview();
        });
    }

    private void setupModeChips() {
        Chip chipNeon = findViewById(R.id.chipNeon);
        Chip chipMirror = findViewById(R.id.chipMirror);
        Chip chipFill = findViewById(R.id.chipFill);
        Chip chipDarkBg = findViewById(R.id.chipDarkBg);

        chipNeon.setOnCheckedChangeListener((b, checked) -> drawingView.setNeonMode(checked));
        chipMirror.setOnCheckedChangeListener((b, checked) -> drawingView.setMirrorMode(checked));
        chipFill.setOnCheckedChangeListener((b, checked) -> drawingView.setFillShapes(checked));
        chipDarkBg.setOnCheckedChangeListener((b, checked) -> drawingView.setDarkCanvas(checked));
    }

    private void setupPalette() {
        LinearLayout palette = findViewById(R.id.colorPalette);
        int size = (int) (30 * density);
        int margin = (int) (5 * density);

        for (int color : PALETTE) {
            View swatch = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            swatch.setLayoutParams(lp);
            swatch.setBackground(makeSwatchDrawable(color, false));
            swatch.setTag(color);
            swatch.setOnClickListener(this::selectColor);
            palette.addView(swatch);
            swatches.add(swatch);
        }
        selectColor(swatches.get(0));
    }

    private void selectColor(View selected) {
        int color = (int) selected.getTag();
        drawingView.setColor(color);
        for (View s : swatches) {
            boolean isSelected = s == selected;
            s.setBackground(makeSwatchDrawable((int) s.getTag(), isSelected));
            float scale = isSelected ? 1.22f : 1f;
            s.animate().scaleX(scale).scaleY(scale).setDuration(240)
                    .setInterpolator(new OvershootInterpolator(3f)).start();
        }
        updateSizePreview();
    }

    private GradientDrawable makeSwatchDrawable(int color, boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        if (selected) {
            d.setStroke((int) (2.8f * density), Color.WHITE);
        } else {
            d.setStroke((int) (1f * density), Color.parseColor("#33FFFFFF"));
        }
        return d;
    }

    private void setupSizeSeekBar() {
        SeekBar seek = findViewById(R.id.seekSize);
        seek.setProgress(8 - MIN_SIZE_DP);
        drawingView.setStrokeWidth(8 * density);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                drawingView.setStrokeWidth((progress + MIN_SIZE_DP) * density);
                updateSizePreview();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        updateSizePreview();
    }

    private void updateSizePreview() {
        if (sizePreview == null) return;
        SeekBar seek = findViewById(R.id.seekSize);
        int sizeDp = Math.min(seek.getProgress() + MIN_SIZE_DP, 36);
        int px = (int) (sizeDp * density);
        sizePreview.getLayoutParams().width = px;
        sizePreview.getLayoutParams().height = px;
        sizePreview.requestLayout();

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(drawingView.getColor());
        sizePreview.setBackground(dot);
    }

    private void setupHistoryAndReplayButtons() {
        btnReplay.setOnClickListener(v -> {
            bump(v);
            drawingView.replayDrawing(() ->
                    Toast.makeText(this, R.string.draw_replay_done, Toast.LENGTH_SHORT).show());
        });
        btnUndo.setOnClickListener(v -> {
            bump(v);
            drawingView.undo();
        });
        btnRedo.setOnClickListener(v -> {
            bump(v);
            drawingView.redo();
        });
        btnClear.setOnClickListener(v -> clearWithAnimation());

        drawingView.setOnHistoryChangeListener((canUndo, canRedo) -> {
            setEnabledStyled(btnReplay, canUndo);
            setEnabledStyled(btnUndo, canUndo);
            setEnabledStyled(btnRedo, canRedo);
            setEnabledStyled(btnClear, canUndo);
        });
    }

    private void clearWithAnimation() {
        if (drawingView.isEmpty()) return;
        drawingView.animate()
                .alpha(0f).scaleX(0.92f).scaleY(0.92f)
                .setDuration(220)
                .withEndAction(() -> {
                    drawingView.clear();
                    drawingView.animate()
                            .alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(320)
                            .setInterpolator(new OvershootInterpolator(1.5f))
                            .start();
                    Toast.makeText(this, R.string.draw_cleared, Toast.LENGTH_SHORT).show();
                })
                .start();
    }

    private void bump(View v) {
        v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                        .setInterpolator(new OvershootInterpolator(3f)).start())
                .start();
    }

    private void setEnabledStyled(View v, boolean enabled) {
        v.setEnabled(enabled);
        v.animate().alpha(enabled ? 1f : 0.35f).setDuration(150).start();
    }
}
