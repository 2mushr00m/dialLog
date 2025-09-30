package com.example.diallog.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.model.TranscriptSection;
import com.example.diallog.data.model.Transcript;

import java.util.ArrayList;

public final class TranscriptSectionAdapter extends ListAdapter<TranscriptSection, TranscriptSectionAdapter.VH> {

    public TranscriptSectionAdapter() { super(DIFF); }

    private static final DiffUtil.ItemCallback<TranscriptSection> DIFF = new DiffUtil.ItemCallback<TranscriptSection>() {
        @Override public boolean areItemsTheSame(@NonNull TranscriptSection a, @NonNull TranscriptSection b) {
            if (a.items.size() != b.items.size()) return false;
            if (a.items.isEmpty()) return true;
            Transcript af = a.items.get(0), bf = b.items.get(0);
            Transcript al = a.items.get(a.items.size()-1), bl = b.items.get(b.items.size()-1);
            return af.startMs == bf.startMs && af.endMs == bf.endMs &&
                    al.startMs == bl.startMs && al.endMs == bl.endMs;
        }
        @Override public boolean areContentsTheSame(@NonNull TranscriptSection a, @NonNull TranscriptSection b) {
            if (a.items.size() != b.items.size()) return false;
            for (int i = 0; i < a.items.size(); i++) {
                Transcript x = a.items.get(i);
                Transcript y = b.items.get(i);
                if (x.startMs != y.startMs || x.endMs != y.endMs) return false;
                if (x.text == null ? y.text != null : !x.text.equals(y.text)) return false;
            }
            return true;
        }
    };
    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transcript_section, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(getItem(position));
    }


    static final class VH extends RecyclerView.ViewHolder {
        private final RecyclerView rvTranscript;
        private final TranscriptAdapter child;

        VH(@NonNull View itemView) {
            super(itemView);
            rvTranscript = itemView.findViewById(R.id.rv_items);
            rvTranscript.setItemAnimator(null);
            child = new TranscriptAdapter();
            rvTranscript.setAdapter(child);
        }

        void bind(@NonNull TranscriptSection section) {
            child.submitList(new ArrayList<>(section.items));
        }
    }

}