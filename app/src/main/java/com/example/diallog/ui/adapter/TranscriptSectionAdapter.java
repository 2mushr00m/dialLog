package com.example.diallog.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.model.TranscriptSection;
import com.example.diallog.data.model.Transcript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TranscriptSectionAdapter extends ListAdapter<TranscriptSection, TranscriptSectionAdapter.VH> {
    public interface OnItemClick { void onClick(@NonNull TranscriptSection t); }

    @Nullable private final OnItemClick onItemClick;

    public TranscriptSectionAdapter() { this(null); }
    public TranscriptSectionAdapter(@Nullable OnItemClick onItemClick) {
        super(DIFF);
        this.onItemClick = onItemClick;
    }

    private static final DiffUtil.ItemCallback<TranscriptSection> DIFF =
            new DiffUtil.ItemCallback<TranscriptSection>() {
                @Override public boolean areItemsTheSame(@NonNull TranscriptSection a, @NonNull TranscriptSection b) {
                    List<Transcript> ai = a.items != null ? a.items : Collections.emptyList();
                    List<Transcript> bi = b.items != null ? b.items : Collections.emptyList();
                    if (ai.size() != bi.size()) return false;
                    if (ai.isEmpty()) return true;
                    Transcript af = ai.get(0), bf = bi.get(0);
                    Transcript al = ai.get(ai.size()-1), bl = bi.get(bi.size()-1);
                    return af.startMs == bf.startMs && af.endMs == bf.endMs
                            && al.startMs == bl.startMs && al.endMs == bl.endMs;
                }
                @Override public boolean areContentsTheSame(@NonNull TranscriptSection a, @NonNull TranscriptSection b) {
                    List<Transcript> ai = a.items != null ? a.items : Collections.emptyList();
                    List<Transcript> bi = b.items != null ? b.items : Collections.emptyList();
                    if (ai.size() != bi.size()) return false;
                    for (int i = 0; i < ai.size(); i++) {
                        Transcript x = ai.get(i), y = bi.get(i);
                        if (x.startMs != y.startMs || x.endMs != y.endMs) return false;
                        if (x.text == null ? y.text != null : !x.text.equals(y.text)) return false;
                    }
                    return true;
                }
            };


    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transcript_section, parent, false);
        return new VH(v, onItemClick);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static final class VH extends RecyclerView.ViewHolder {
        private final RecyclerView rvItems;
        private final TranscriptAdapter child;

        VH(@NonNull View itemView, @Nullable OnItemClick onClick) {
            super(itemView);
            rvItems = itemView.findViewById(R.id.rv_items);
            rvItems.setItemAnimator(null);

            child = new TranscriptAdapter();

            rvItems.setAdapter(child);
        }

        void bind(@NonNull TranscriptSection section) {
            // 접근성: 카드에 섹션 요약과 화자 라벨 힌트 부여(헤더 뷰가 없으므로 선택 사항)
            // itemView.setContentDescription(section.allText != null ? section.allText : "");

            // 내부 리스트 바인딩
            child.submitList(new ArrayList<>(section.items));
        }
    }
}