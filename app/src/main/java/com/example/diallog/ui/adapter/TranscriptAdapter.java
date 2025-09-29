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
import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.utils.TimeFormatter;

import java.util.ArrayList;
import java.util.List;

public final class TranscriptAdapter extends ListAdapter<TranscriptSegment, TranscriptAdapter.VH> {
    public interface OnItemClick { void onClick(@NonNull TranscriptSegment seg); }
    @NonNull private final OnItemClick onItemClickOrNoop;

    public TranscriptAdapter() { this(null); }

    public TranscriptAdapter(@Nullable OnItemClick onItemClick) {
        super(DIFF);
        this.onItemClickOrNoop = onItemClick != null ? onItemClick : seg -> {};
    }
    private static final DiffUtil.ItemCallback<TranscriptSegment> DIFF =
            new DiffUtil.ItemCallback<TranscriptSegment>() {
                @Override public boolean areItemsTheSame(@NonNull TranscriptSegment a, @NonNull TranscriptSegment b) {
                    return a.startMs == b.startMs && a.endMs == b.endMs;
                }
                @Override public boolean areContentsTheSame(@NonNull TranscriptSegment a, @NonNull TranscriptSegment b) {
                    return a.startMs == b.startMs && a.endMs == b.endMs &&
                            ((a.text == null && b.text == null) || (a.text != null && a.text.equals(b.text)));
                }
            };

    public static final class VH extends RecyclerView.ViewHolder {
        private final TextView tvTime, tvText;
        private TranscriptSegment bound;

        public VH(@NonNull View itemView, @NonNull TranscriptAdapter adapter) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvText = itemView.findViewById(R.id.tv_text);
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                if (bound != null) adapter.onItemClickOrNoop.onClick(bound);
            });
        }

        void bind(@NonNull TranscriptSegment seg) {
            bound = seg;
            tvTime.setText(TimeFormatter.toMmSs(seg.startMs));
            tvText.setText(seg.text);
        }
    }


    @Override public void submitList(@Nullable List<TranscriptSegment> list) {
        super.submitList(copy(list));
    }
    @Override public void submitList(@Nullable List<TranscriptSegment> list, @Nullable Runnable cb) {
        super.submitList(copy(list), cb);
    }
    private @Nullable List<TranscriptSegment> copy(@Nullable List<TranscriptSegment> list) {
        return list == null ? null : new ArrayList<>(list);
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transcript, parent, false);
        return new VH(v, this);
    }
    @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(getItem(pos)); }
}
