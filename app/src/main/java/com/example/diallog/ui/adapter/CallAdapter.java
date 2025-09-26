package com.example.diallog.ui.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.model.CallRecord;
import com.example.diallog.utils.TimeFormatter;

public final class CallAdapter extends ListAdapter<CallRecord, CallAdapter.VH> {
    public interface OnItemClick { void onClick(@NonNull Uri uri); }
    @NonNull private final OnItemClick onItemClick;


    public CallAdapter(@NonNull OnItemClick onItemClick) {
        super(DIFF);
        this.onItemClick = onItemClick;
    }

    private static final DiffUtil.ItemCallback<CallRecord> DIFF =
            new DiffUtil.ItemCallback<CallRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull CallRecord a, @NonNull CallRecord b) {
                    return a.uri.equals(b.uri);
                }
                @Override
                public boolean areContentsTheSame(@NonNull CallRecord a, @NonNull CallRecord b) {
                    return a.durationMs == b.durationMs
                            && a.startedAtEpochMs == b.startedAtEpochMs
                            && safeEquals(a.fileName, b.fileName);
                }
                private boolean safeEquals(String x, String y) {
                    if (x == null) return y == null;
                    return x.equals(y);
                }
            };


    public static final class VH extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvMeta;
        private CallRecord bound;

        public VH(@NonNull View itemView, @NonNull CallAdapter adapter) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvMeta = itemView.findViewById(R.id.tv_meta);

            // 클릭 리스너: NO_POSITION(-1) 방어
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                if (bound != null) adapter.onItemClick.onClick(bound.uri);
            });
        }

        public void bind(@NonNull CallRecord item) {
            this.bound = item;
            tvTitle.setText(item.fileName != null ? item.fileName : "(unknown)");

            String when = TimeFormatter.toYmdHm(item.startedAtEpochMs);
            String dur  = TimeFormatter.toMmSs(item.durationMs);
            tvMeta.setText(when + " · " + dur);
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new VH(v, this);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

}