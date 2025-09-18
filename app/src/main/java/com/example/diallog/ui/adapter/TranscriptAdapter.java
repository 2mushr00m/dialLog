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

/**
 * 목적:
 *  - STT 결과(TranscriptSegment 목록)를 ListAdapter로 표시합니다.
 *  - submitList(newList)만 호출하면 Diff 계산과 부분 갱신을 자동으로 처리합니다.
 */
public final class TranscriptAdapter extends ListAdapter<TranscriptSegment, TranscriptAdapter.VH> {
    public interface OnItemClick { void onClick(@NonNull TranscriptSegment seg); }
    @NonNull private final OnItemClick onItemClickOrNoop;

    public TranscriptAdapter() { this(null); }

    public TranscriptAdapter(@SuppressWarnings("NullableProblems") OnItemClick onItemClick) {
        super(DIFF);
        this.onItemClickOrNoop = onItemClick != null ? onItemClick : seg -> { /* no-op */ };
    }
    private static final DiffUtil.ItemCallback<TranscriptSegment> DIFF =
            new DiffUtil.ItemCallback<TranscriptSegment>() {
                @Override
                public boolean areItemsTheSame(@NonNull TranscriptSegment a, @NonNull TranscriptSegment b) {
                    return a.startMs == b.startMs && a.endMs == b.endMs;
                }

                @Override
                public boolean areContentsTheSame(@NonNull TranscriptSegment a, @NonNull TranscriptSegment b) {
                    return a.startMs == b.startMs && a.endMs == b.endMs
                            && safeEquals(a.text, b.text);
                }

                private boolean safeEquals(String x, String y) {
                    if (x == null) return y == null;
                    return x.equals(y);
                }
            };


    public static final class VH extends RecyclerView.ViewHolder {
        private final TextView tvText;
        private TranscriptSegment bound;

        public VH(@NonNull View itemView, @NonNull TranscriptAdapter adapter) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_text);

            // 클릭 리스너 방어: NO_POSITION이면 즉시 반환
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                if (bound != null) adapter.onItemClickOrNoop.onClick(bound);
            });
        }
        public void bind(@NonNull TranscriptSegment item) {
            this.bound = item;
            tvText.setText(item.text);

            TextView time = itemView.findViewById(R.id.tv_time);
            TextView text = itemView.findViewById(R.id.tv_text);
            time.setText(TimeFormatter.toMmSs(item.startMs));
            text.setText(item.text);
        }
    }

    @Override
    public void submitList(@Nullable List<TranscriptSegment> list) {
        super.submitList(copy(list));
    }

    @Override
    public void submitList(@Nullable List<TranscriptSegment> list, @Nullable Runnable commitCallback) {
        super.submitList(copy(list), commitCallback);
    }

    @Nullable
    private List<TranscriptSegment> copy(@Nullable List<TranscriptSegment> list) {
        if (list == null) {
            return null;
        }
        return new ArrayList<>(list);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transcript, parent, false);
        return new VH(v, this);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }
}
