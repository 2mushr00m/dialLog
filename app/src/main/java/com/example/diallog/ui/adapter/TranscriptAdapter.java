package com.example.diallog.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.model.Transcript;

import java.util.ArrayList;
import java.util.List;

public final class TranscriptAdapter extends ListAdapter<Transcript, TranscriptAdapter.VH> {
    public interface OnItemClick { void onClick(@NonNull Transcript t); }
    @Nullable private final OnItemClick onItemClick;

    public TranscriptAdapter() { this(null); }

    public TranscriptAdapter(@Nullable OnItemClick onItemClick) {
        super(DIFF);
        this.onItemClick = onItemClick;
    }
    private static final DiffUtil.ItemCallback<Transcript> DIFF =
            new DiffUtil.ItemCallback<Transcript>() {
                @Override public boolean areItemsTheSame(@NonNull Transcript a, @NonNull Transcript b) {
                    return a.startMs == b.startMs && a.endMs == b.endMs;
                }
                @Override public boolean areContentsTheSame(@NonNull Transcript a, @NonNull Transcript b) {
                    return a.startMs == b.startMs && a.endMs == b.endMs &&
                            ((a.text == null && b.text == null) || (a.text != null && a.text.equals(b.text)));
                }
            };

    public static final class VH extends RecyclerView.ViewHolder {
        private final TextView tvTime, tvText;
        private Transcript bound;

        VH(@NonNull View itemView, @Nullable OnItemClick onItemClick) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvText = itemView.findViewById(R.id.tv_text);

            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || bound == null) return;
                if (onItemClick != null) onItemClick.onClick(bound);
            });
        }

        void bind(@NonNull Transcript t) {
            bound = t;

            long totalSec = t.startMs / 1000;
            tvTime.setText(tvTime.getContext().getString(R.string.label_timestamp_time,
                    totalSec / 60, totalSec % 60));
            tvText.setText(t.text == null ? "" : t.text);
        }

    }


    @Override public void submitList(@Nullable List<Transcript> list) {
        super.submitList(copy(list));
    }
    @Override public void submitList(@Nullable List<Transcript> list, @Nullable Runnable cb) {
        super.submitList(copy(list), cb);
    }
    private @Nullable List<Transcript> copy(@Nullable List<Transcript> list) {
        return list == null ? null : new ArrayList<>(list);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transcript, parent, false);
        return new VH(v, onItemClick);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }
}
