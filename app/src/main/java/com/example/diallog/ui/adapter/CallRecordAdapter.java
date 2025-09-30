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

import java.util.Calendar;
import java.util.Objects;

public final class CallRecordAdapter extends ListAdapter<CallRecord, CallRecordAdapter.VH> {
    public interface OnItemClick { void onClick(@NonNull Uri uri); }
    @NonNull private final OnItemClick onItemClick;


    public CallRecordAdapter(@NonNull OnItemClick onItemClick) {
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
                    return a.uri.equals(b.uri)
                            && a.durationMs == b.durationMs
                            && a.startedAtEpochMs == b.startedAtEpochMs
                            && Objects.equals(a.fileName, b.fileName);
                }
            };


    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_call_record, parent, false);
        return new VH(v, this);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    public static final class VH extends RecyclerView.ViewHolder {
        private final TextView tvTitle, tvMeta, tvSummary;
        private CallRecord bound;

        public VH(@NonNull View itemView, @NonNull CallRecordAdapter adapter) {
            super(itemView);
            tvTitle   = itemView.findViewById(R.id.tv_title);
            tvMeta    = itemView.findViewById(R.id.tv_meta);
            tvSummary = itemView.findViewById(R.id.tv_summary);

            // 클릭 리스너: NO_POSITION(-1) 방어
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                if (bound != null) adapter.onItemClick.onClick(bound.uri);
            });
        }

        void bind(@NonNull CallRecord cr) {
            // TODO: 통화 목록과 이름 매칭
            // 매칭되는 것이 있다면, 유추되는 전화번호 / 전화번호 등록명으로 연결
            // 매칭되는 것이 없다면, 파일명으로 연결
            this.bound = cr;
            tvTitle.setText(cr.fileName != null ? cr.fileName : "Call");

            long now = System.currentTimeMillis();
            Calendar calNow = Calendar.getInstance();
            Calendar calStart = Calendar.getInstance();
            calStart.setTimeInMillis(cr.startedAtEpochMs);

            boolean isToday =
                    calNow.get(Calendar.YEAR) == calStart.get(Calendar.YEAR) &&
                            calNow.get(Calendar.DAY_OF_YEAR) == calStart.get(Calendar.DAY_OF_YEAR);

            String metaText;
            if (isToday) {
                long diffSecAgo = Math.max(0, (now - cr.startedAtEpochMs) / 1000L);
                long diffMinAgo = diffSecAgo / 60;
                long diffHourAgo = diffMinAgo / 60;

                if (diffSecAgo < 60) {
                    metaText = tvMeta.getContext().getString(R.string.label_seconds_ago, diffSecAgo);
                } else if (diffMinAgo < 60) {
                    metaText = tvMeta.getContext().getString(R.string.label_minutes_ago, diffMinAgo);
                } else {
                    metaText = tvMeta.getContext().getString(R.string.label_hours_ago, diffHourAgo);
                }
            } else {
                int hour = calStart.get(Calendar.HOUR);
                if (hour == 0) hour = 12;
                int minute = calStart.get(Calendar.MINUTE);
                String ampm = (calStart.get(Calendar.AM_PM) == Calendar.AM) ? "AM" : "PM";
                metaText = tvMeta.getContext().getString(R.string.label_created_time, hour, minute, ampm);
            }
            tvMeta.setText(metaText);

            // TODO: 한 줄 요약 표시
            // STT 완료라면, chatGPT로 한 줄 요약 표시
            // ChatGPT 오류 시 한 줄 요약 표시 불가 고정 메시지
            // STT 미완료라면, 음성 길이 + 고정 메시지

            boolean doneStt = false;

            String summaryText;
            if (doneStt) {
                // 임시
                summaryText = tvSummary.getContext().getString(R.string.label_view_summary);
            } else {
                long diffSec = Math.max(0, cr.durationMs / 1000L);
                long diffMin = diffSec / 60;
                long diffHour = diffMin / 60;

                if (diffSec < 60) {
                    summaryText = tvSummary.getContext().getString(R.string.label_s_no_stt, diffSec);
                } else if (diffMin < 60) {
                    summaryText = tvSummary.getContext().getString(R.string.label_ms_no_stt, diffMin, diffSec % 60);
                } else {
                    summaryText = tvSummary.getContext().getString(R.string.label_hms_no_stt, diffHour, diffMin % 60, diffSec % 60);
                }
                tvSummary.setTextColor(tvSummary.getContext().getColor(R.color.lightGray_700));
            }
            tvSummary.setText(summaryText);
        }
    }

}
