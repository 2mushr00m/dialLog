package com.example.diallog.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.example.diallog.R;
import com.example.diallog.data.model.Transcript;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 단일 어댑터. 화자(speakerLabel)에 따라 행 배경색을 결정. */
public final class TranscriptAdapter extends ListAdapter<Transcript, TranscriptAdapter.VH> {
    public static final String CHIP_LOW_CONFIDENCE = "low_confidence";

    public interface OnTranscriptClick { void onClick(@NonNull Transcript t); }
    /** 새 기준: ChipSpec payload 포함 */
    public interface OnChipClick { void onClick(@NonNull Transcript t, @NonNull ChipSpec spec); }
    public interface ChipProvider { @NonNull List<ChipSpec> chipsFor(@NonNull Transcript t); }

    public static final class ChipSpec {
        public final @NonNull String id, label;
        public final @Nullable Bundle extras;
        public ChipSpec(@NonNull String id, @NonNull String label){ this(id, label, null); }
        public ChipSpec(@NonNull String id, @NonNull String label, @Nullable Bundle extras){
            this.id = id; this.label = label; this.extras = extras;
        }
    }

    @Nullable private final OnTranscriptClick onTranscriptClick;
    @Nullable private final OnChipClick onChipClick;
    @Nullable private final ChipProvider chipProvider;

    private int[] palette = new int[]{
            0xFFE3F2FD, 0xFFF1F8E9, 0xFFFFF3E0, 0xFFF3E5F5, 0xFFE0F2F1, 0xFFFFEBEE
    };
    private boolean autoTextContrast = true;

    public TranscriptAdapter(@Nullable OnTranscriptClick tClick,
                             @Nullable OnChipClick cClick,
                             @Nullable ChipProvider chipProvider) {
        super(DIFF);
        this.onTranscriptClick = tClick;
        this.onChipClick = cClick;
        this.chipProvider = chipProvider;
        setHasStableIds(false);
    }

    public void setPalette(@NonNull int[] colors){
        this.palette = colors.length == 0 ? this.palette : colors;
        notifyDataSetChanged();
    }
    public void setAutoTextContrast(boolean enabled){
        this.autoTextContrast = enabled;
        notifyDataSetChanged();
    }
    public void submitTranscripts(@Nullable List<Transcript> segs){ submitList(copy(segs)); }

    // ===== Diff =====
    private static final DiffUtil.ItemCallback<Transcript> DIFF = new DiffUtil.ItemCallback<Transcript>() {
        @Override public boolean areItemsTheSame(@NonNull Transcript a, @NonNull Transcript b) {
            return a.startMs == b.startMs && a.endMs == b.endMs;
        }
        @Override public boolean areContentsTheSame(@NonNull Transcript a, @NonNull Transcript b) {
            return a.startMs == b.startMs && a.endMs == b.endMs
                    && Objects.equals(a.text, b.text)
                    && Objects.equals(a.speakerLabel, b.speakerLabel);
        }
    };

    // ===== Adapter =====
    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transcript, parent, false);
        return new VH(v, onTranscriptClick, onChipClick, chipProvider,
                this::backgroundColorForSpeaker, this::textColorForBackground);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(getItem(pos)); }

    // ===== 색상 결정 =====
    private int backgroundColorForSpeaker(@Nullable String label){
        if (palette == null || palette.length == 0) return Color.WHITE;
        if (label != null) {
            try {
                int n = Integer.parseInt(label.trim());
                if (n > 0) return palette[(n - 1) % palette.length];
            } catch (NumberFormatException ignore) { /* hash fallback */ }
            int idx = Math.abs(label.hashCode()) % palette.length;
            return palette[idx];
        }
        return palette[0];
    }

    private int textColorForBackground(int bg){
        if (!autoTextContrast) return Color.BLACK;
        double lum = ColorUtils.calculateLuminance(bg);
        return lum < 0.5 ? Color.WHITE : Color.BLACK;
    }

    // ===== 내부 함수형 대체(호환성) =====
    interface BgPicker { int apply(@Nullable String label); }
    interface TextPicker { int apply(int bgColor); }

    // ===== VH =====
    static final class VH extends RecyclerView.ViewHolder {
        private final TextView tvTime, tvText;
        @Nullable private final ChipGroup chipGroup;
        @Nullable private final OnTranscriptClick onTranscriptClick;
        @Nullable private final OnChipClick onChipClick;
        @Nullable private final ChipProvider chipProvider;
        @Nullable private Transcript bound;
        private final BgPicker bgPicker;
        private final TextPicker textPicker;

        VH(@NonNull View v,
           @Nullable OnTranscriptClick tClick,
           @Nullable OnChipClick cClick,
           @Nullable ChipProvider chipProvider,
           @NonNull BgPicker bgPicker,
           @NonNull TextPicker textPicker) {
            super(v);
            this.onTranscriptClick = tClick;
            this.onChipClick = cClick;
            this.chipProvider = chipProvider;
            this.bgPicker = bgPicker;
            this.textPicker = textPicker;

            tvTime = v.findViewById(R.id.tv_time);
            tvText = v.findViewById(R.id.tv_text);
            ChipGroup cg = null; try { cg = v.findViewById(R.id.chip_group); } catch (Throwable ignore) {}
            chipGroup = cg;

            View bubble = null;
            try { bubble = v.findViewById(R.id.cv_text); } catch (Throwable ignore) {}
            if (bubble == null) {
                try { bubble = v.findViewById(R.id.tv_text); } catch (Throwable ignore) {}
            }
            if (bubble != null) {
                View finalBubble = bubble;
                finalBubble.setOnClickListener(v1 -> {
                    if (bound != null && onTranscriptClick != null) onTranscriptClick.onClick(bound);
                });
            }
        }

        void bind(@NonNull Transcript t) {
            bound = t;

            int bg = bgPicker.apply(t.speakerLabel);
            int fg = textPicker.apply(bg);

            itemView.setBackgroundColor(bg);
            tvTime.setTextColor(fg);
            tvText.setTextColor(fg);

            long totalSec = Math.max(0, t.startMs / 1000L);
            tvTime.setText(tvTime.getContext()
                    .getString(R.string.label_timestamp_time, totalSec/60, totalSec%60));
            tvText.setText(t.text == null ? "" : t.text);

            if (chipGroup != null) {
                chipGroup.removeAllViews();
                if (chipProvider != null) {
                    List<ChipSpec> chips = chipProvider.chipsFor(t);
                    if (chips != null && !chips.isEmpty()) {
                        LayoutInflater inf = LayoutInflater.from(chipGroup.getContext());
                        for (ChipSpec spec : chips) {
                            if (spec == null || isEmpty(spec.id) || isEmpty(spec.label)) continue;
                            Chip chip;
                            try {
                                chip = (Chip) inf.inflate(R.layout.item_transcript_chip, chipGroup, false);
                            } catch (Throwable ignore) {
                                chip = new Chip(chipGroup.getContext());
                            }
                            chip.setText(spec.label);
                            chip.setOnClickListener(v -> {
                                if (bound != null && onChipClick != null) onChipClick.onClick(bound, spec);
                            });
                            chipGroup.addView(chip);
                        }
                        chipGroup.setVisibility(View.VISIBLE);
                    } else {
                        chipGroup.setVisibility(View.GONE);
                    }
                } else {
                    chipGroup.setVisibility(View.GONE);
                }
            }
        }

        private static boolean isEmpty(@Nullable String s){ return s == null || s.trim().isEmpty(); }
    }

    // 안전 복사
    @Override public void submitList(@Nullable List<Transcript> list) { super.submitList(copy(list)); }
    @Override public void submitList(@Nullable List<Transcript> list, @Nullable Runnable cb) { super.submitList(copy(list), cb); }
    private static @Nullable List<Transcript> copy(@Nullable List<Transcript> list) {
        return list == null ? null : new ArrayList<>(list);
    }
}
