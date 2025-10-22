package com.example.diallog.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.model.CallRecord;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;



public final class CallRecordAdapter extends ListAdapter<CallRecordAdapter.Row, RecyclerView.ViewHolder> {
    public interface OnItemClick { void onClick(@NonNull CallRecord cr); }
    public interface OnMenuRequest { void onMenu(@NonNull View anchor, @NonNull CallRecord cr); }


    private static final int VT_HEADER = 1;
    private static final int VT_ITEM   = 2;

    @NonNull private final OnItemClick onItemClick;
    @NonNull private final OnMenuRequest onMenuRequest;
    private Set<String> transcribingKeys = Collections.emptySet();

    public CallRecordAdapter(@NonNull OnItemClick onItemClick, @NonNull OnMenuRequest onMenuRequest) {
        super(DIFF);
        this.onItemClick = onItemClick;
        this.onMenuRequest = onMenuRequest; // ← 추가
    }

    /** 리스트 + 진행 상태를 함께 반영 */
    public void submitRecords(@NonNull List<CallRecord> records) {
        submitList(buildRows(records));
    }
    public void setTranscribingUris(@Nullable Set<android.net.Uri> s) {
        HashSet<String> keys = new HashSet<>();
        if (s != null) for (android.net.Uri u : s) keys.add(u.toString());
        this.transcribingKeys = keys;
        notifyDataSetChanged();
    }

    private List<Row> buildRows(@NonNull List<CallRecord> records) {
        LinkedHashMap<String, List<CallRecord>> buckets = new LinkedHashMap<>();
        Calendar now = Calendar.getInstance();
        Calendar yesterday = (Calendar) now.clone(); yesterday.add(Calendar.DAY_OF_YEAR, -1);
        Calendar c = Calendar.getInstance();

        for (CallRecord cr : records) {
            c.setTimeInMillis(cr.startedAtEpochMs);
            final String header;
            if (now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                    && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)) {
                header = "TODAY";
            } else if (yesterday.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                    && yesterday.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)) {
                header = "YESTERDAY";
            } else {
                header = String.format(Locale.ROOT, "MD:%02d-%02d", c.get(Calendar.MONTH) + 1, c.get(Calendar.DATE));
            }
            buckets.computeIfAbsent(header, k -> new ArrayList<>()).add(cr);
        }

        List<Row> rows = new ArrayList<>();
        for (String key : buckets.keySet()) {
            rows.add(Row.header(key, buckets.get(key).get(0).startedAtEpochMs));
            for (CallRecord cr : buckets.get(key)) rows.add(Row.item(cr));
        }
        return rows;
    }

    @Override public int getItemViewType(int position) {
        Row r = getItem(position);
        return r.isHeader ? VT_HEADER : VT_ITEM;
    }


    public static final class Row {
        public final boolean isHeader;
        public final @Nullable String headerKey;
        public final long headerRefEpochMs;
        public final @Nullable CallRecord item;

        private Row(boolean isHeader, @Nullable String headerKey, long headerRefEpochMs, @Nullable CallRecord item) {
            this.isHeader = isHeader;
            this.headerKey = headerKey;
            this.headerRefEpochMs = headerRefEpochMs;
            this.item = item;
        }
        public static Row header(@NonNull String key, long refMs) { return new Row(true, key, refMs, null); }
        public static Row item(@NonNull CallRecord cr) { return new Row(false, null, 0L, cr); }
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.isHeader != b.isHeader) return false;
            if (a.isHeader) return Objects.equals(a.headerKey, b.headerKey);
            return Objects.requireNonNull(a.item).uri.equals(Objects.requireNonNull(b.item).uri);
        }
        @Override public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.isHeader) return Objects.equals(a.headerKey, b.headerKey);
            CallRecord x = Objects.requireNonNull(a.item), y = Objects.requireNonNull(b.item);
            return Objects.equals(x.fileName, y.fileName)
                    && x.startedAtEpochMs == y.startedAtEpochMs
                    && x.durationMs == y.durationMs
                    && Objects.equals(x.summary, y.summary);
        }
    };

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VT_HEADER) {
            View v = inf.inflate(R.layout.item_call_record_header, parent, false);
            return new HeaderVH(v);
        } else {
            View v = inf.inflate(R.layout.item_call_record, parent, false);
            return new ItemVH(v, onItemClick, onMenuRequest);
        }
    }
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row r = getItem(position);
        if (r.isHeader) {
            ((HeaderVH) holder).bind(r.headerKey, r.headerRefEpochMs);
            return;
        }
        boolean running = transcribingKeys != null && transcribingKeys.contains(r.item.uri.toString());
        ItemVH vh = (ItemVH) holder;
        vh.bind(r.item, running);

        SegKind kind = segKindAt(position);

        boolean isMenu = menuUris.contains(r.item.uri);
        boolean isEdit = editUris.contains(r.item.uri);
        boolean highlighted = isMenu || isEdit;

        View root = holder.itemView;
        root.setBackground(rippleBg(root, kind, highlighted));

        root.setSelected(isMenu);
        root.setActivated(isEdit);
    }
    static final class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvHeader;
        HeaderVH(@NonNull View v) {
            super(v);
            tvHeader = v.findViewById(R.id.tv_how_long);
        }
        void bind(@NonNull String key, long refEpochMs) {
            if ("TODAY".equals(key)) {
                tvHeader.setText(itemView.getContext().getString(R.string.label_today));
                return;
            }
            if ("YESTERDAY".equals(key)) {
                tvHeader.setText(itemView.getContext().getString(R.string.label_yesterday));
                return;
            }
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refEpochMs);
            int month = c.get(Calendar.MONTH) + 1;
            int day = c.get(Calendar.DATE);
            tvHeader.setText(itemView.getContext().getString(R.string.label_date_month_day, month, day));
        }
    }

    static final class ItemVH extends RecyclerView.ViewHolder {
        final TextView tvTitle, tvMeta, tvSummary;
        @Nullable final ProgressBar pgStt;
        @Nullable private final OnItemClick onItemClick;
        @NonNull private final OnMenuRequest onMenuRequest;
        @NonNull private CallRecord bound;

        ItemVH(@NonNull View v, @Nullable OnItemClick onItemClick, @Nullable OnMenuRequest onMenuRequest) {
            super(v);
            this.onItemClick = onItemClick;
            this.onMenuRequest = onMenuRequest;
            tvTitle = v.findViewById(R.id.tv_title);
            tvMeta = v.findViewById(R.id.tv_meta);
            tvSummary = v.findViewById(R.id.tv_summary);
            pgStt = v.findViewById(R.id.pg_stt);

            v.setOnClickListener(iv -> { if (bound != null && onItemClick != null) onItemClick.onClick(bound); });

            // ← 추가: 롱클릭 시 메뉴 요청
            v.setOnLongClickListener(iv -> {
                if (bound == null || onMenuRequest == null) return true;
                onMenuRequest.onMenu(iv, bound);
                return true;
            });
        }

        void bind(@NonNull CallRecord cr, boolean isTranscribing) {
            this.bound = cr;
            // 제목
            tvTitle.setText(cr.fileName != null ? cr.fileName : "CallRecord");

            // 메타: 오늘이면 n분 전, 아니면 시:분 AM/PM
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

            // STT 요약 / STT 진행 / 미STT 문구
            String summaryText;
            if (!TextUtils.isEmpty(cr.summary)) {
                summaryText = cr.summary;
                tvSummary.setTextColor(tvSummary.getContext().getColor(R.color.subtext));
            } else if (isTranscribing) {
                summaryText = "";
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
                tvSummary.setTextColor(tvSummary.getContext().getColor(R.color.subtext_light));
            }
            tvSummary.setText(summaryText);

            pgStt.setIndeterminate(isTranscribing);
            pgStt.setVisibility(isTranscribing ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> { if (onItemClick != null) onItemClick.onClick(cr); });
        }
    }


    /** CallRecord Ripple */
    private final Set<Uri> menuUris = new HashSet<>();
    private final Set<Uri> editUris = new HashSet<>();
    public void setMenuState(@NonNull Uri uri, boolean on){
        if (on) menuUris.add(uri); else menuUris.remove(uri);
        notifyDataSetChanged();
    }
    public void setEditState(@NonNull Uri uri, boolean on){
        if (on) editUris.add(uri); else editUris.remove(uri);
        notifyDataSetChanged();
    }
    private int cNormal(View v){ return ContextCompat.getColor(v.getContext(), R.color.surface_variant); }
    private int cTint  (View v){ return ContextCompat.getColor(v.getContext(), R.color.click_ripple); }
    private float dp(View v, float d){ return d * v.getResources().getDisplayMetrics().density; }
    private Drawable mask20(View v){ return ContextCompat.getDrawable(v.getContext(), R.drawable.mask_round_20dp); }
    private float[] radiiFor(View v, SegKind k){
        float r = dp(v, 20f);
        switch (k){
            case FIRST:  return new float[]{r,r, r,r, 0,0, 0,0};
            case MIDDLE: return new float[]{0,0, 0,0, 0,0, 0,0};
            case LAST:   return new float[]{0,0, 0,0, r,r, r,r};
            case SINGLE:
            default:     return new float[]{r,r, r,r, r,r, r,r};
        }
    }

    private GradientDrawable shapeFor(View v, SegKind k, int fill){
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadii(radiiFor(v, k));
        return g;
    }

    private Drawable rippleBg(View v, SegKind k, boolean highlighted){
        int base = highlighted
                ? ContextCompat.getColor(v.getContext(), R.color.click_ripple)
                : ContextCompat.getColor(v.getContext(), R.color.surface_variant);

        GradientDrawable content = shapeFor(v, k, base);
        ColorStateList ripple = ColorStateList.valueOf(
                ContextCompat.getColor(v.getContext(), R.color.click_ripple));
        Drawable mask = mask20(v);

        RippleDrawable rd = new RippleDrawable(ripple, content, mask);
        rd.mutate();
        return rd;
    }



    /** CallRecord Section Card Background */
    private enum SegKind { SINGLE, FIRST, MIDDLE, LAST }
    private SegKind segKindAt(int position) {
        // header 또는 범위 밖이면 의미 없음
        if (position < 0 || position >= getItemCount()) return SegKind.SINGLE;
        Row cur = getItem(position);
        if (cur.isHeader) return SegKind.SINGLE;

        // 바로 앞/뒤가 헤더인지 체크
        boolean prevIsHeader = (position - 1 < 0) || getItem(position - 1).isHeader;
        boolean nextIsHeader = (position + 1 >= getItemCount()) || getItem(position + 1).isHeader;

        if (prevIsHeader && nextIsHeader) return SegKind.SINGLE;
        if (prevIsHeader) return SegKind.FIRST;
        if (nextIsHeader) return SegKind.LAST;
        return SegKind.MIDDLE;
    }

    private void applyCardBackground(@NonNull RecyclerView.ViewHolder holder, @NonNull SegKind k) {
        View root = holder.itemView;
        switch (k) {
            case SINGLE:
                safeSetBg(root, R.drawable.bg_section_card_single, R.drawable.bg_item_call);
                break;
            case FIRST:
                safeSetBg(root, R.drawable.bg_section_card_top, R.drawable.bg_item_call);
                break;
            case MIDDLE:
                safeSetBg(root, R.drawable.bg_section_card_middle, R.drawable.bg_item_call);
                break;
            case LAST:
                safeSetBg(root, R.drawable.bg_section_card_bottom, R.drawable.bg_item_call);
                break;
        }
    }

    private void safeSetBg(@NonNull View v, int primary, int fallback) {
        try { v.setBackgroundResource(primary); }
        catch (Exception ignore) { v.setBackgroundResource(fallback); }
    }
}
