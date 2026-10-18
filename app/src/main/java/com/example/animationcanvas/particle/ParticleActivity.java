package com.example.animationcanvas.particle;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.animationcanvas.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Màn hình pháo hoa: chọn kiểu nổ hình học, điều chỉnh trọng lực, tự động bắn và bắn loạt 5 quả.
 */
public class ParticleActivity extends AppCompatActivity {

    private static final int DEFAULT_GRAVITY = 500;

    private static final String[] PATTERN_LABELS = {
            "Ngẫu nhiên",
            "Hoa cúc",
            "Vòng kép",
            "Trái tim",
            "Ngôi sao",
            "Xoắn ốc"
    };

    private ParticleView particleView;
    private TextView tvGravity;
    private TextView tvHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_particle);

        particleView = findViewById(R.id.particleView);
        tvGravity = findViewById(R.id.tvGravity);
        tvHint = findViewById(R.id.tvHint);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        setupPatternChips();
        startHintPulse();

        particleView.setOnFirstTouchListener(() -> {
            tvHint.animate().cancel();
            tvHint.animate().alpha(0f).translationY(-40f).setDuration(380)
                    .withEndAction(() -> tvHint.setVisibility(View.GONE)).start();
        });

        SeekBar seekGravity = findViewById(R.id.seekGravity);
        seekGravity.setProgress(DEFAULT_GRAVITY);
        updateGravity(DEFAULT_GRAVITY);
        seekGravity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateGravity(progress);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        MaterialSwitch switchAuto = findViewById(R.id.switchAuto);
        switchAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            particleView.setAutoFireworks(isChecked);
            if (isChecked) hideHintNow();
        });

        findViewById(R.id.btnLaunch).setOnClickListener(v -> {
            hideHintNow();
            particleView.launchRocket();
        });

        findViewById(R.id.btnSalvo).setOnClickListener(v -> {
            hideHintNow();
            particleView.launchSalvo();
        });

        findViewById(R.id.btnClearParticles).setOnClickListener(v -> particleView.clearParticles());
    }

    private void setupPatternChips() {
        ChipGroup group = findViewById(R.id.chipGroupPattern);
        ColorStateList bg = ContextCompat.getColorStateList(this, R.color.tool_bg);
        ColorStateList text = ContextCompat.getColorStateList(this, R.color.tool_text);

        for (int i = 0; i < PATTERN_LABELS.length; i++) {
            final int patternIndex = i;
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setText(PATTERN_LABELS[i]);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColor(bg);
            chip.setTextColor(text);
            chip.setChipStrokeWidth(0f);
            chip.setTextSize(11.5f);
            chip.setOnClickListener(v -> {
                particleView.setPattern(patternIndex);
                hideHintNow();
                particleView.launchRocket();
            });
            group.addView(chip);
            if (i == 0) chip.setChecked(true);
        }
    }

    private void updateGravity(int value) {
        particleView.setGravityDp(value);
        tvGravity.setText(String.valueOf(value));
    }

    private void startHintPulse() {
        tvHint.setAlpha(0.45f);
        tvHint.animate().alpha(1f).setDuration(900).withEndAction(() -> {
            if (tvHint.getVisibility() == View.VISIBLE && tvHint.getTranslationY() == 0f) {
                tvHint.animate().alpha(0.45f).setDuration(900)
                        .withEndAction(this::startHintPulse).start();
            }
        }).start();
    }

    private void hideHintNow() {
        if (tvHint.getVisibility() == View.VISIBLE) {
            tvHint.animate().cancel();
            tvHint.animate().alpha(0f).setDuration(280)
                    .withEndAction(() -> tvHint.setVisibility(View.GONE)).start();
        }
    }

    @Override
    protected void onDestroy() {
        tvHint.animate().cancel();
        super.onDestroy();
    }
}
