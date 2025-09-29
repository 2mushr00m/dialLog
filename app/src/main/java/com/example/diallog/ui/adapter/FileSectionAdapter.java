package com.example.diallog.ui.adapter;

import android.net.Uri;
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
import com.example.diallog.data.model.CallRecord;
import com.example.diallog.data.model.FileSection;

import java.util.ArrayList;

public final class FileSectionAdapter extends ListAdapter<FileSection, FileSectionAdapter.VH> {

    public interface OnCallClick { void onClick(@NonNull Uri uri); }
    @Nullable private final OnCallClick onCallClick;


    public FileSectionAdapter(@Nullable OnCallClick onCallClick) {
        super(DIFF);
        this.onCallClick = onCallClick;
    }

    private static final DiffUtil.ItemCallback<FileSection> DIFF = new DiffUtil.ItemCallback<FileSection>() {
        @Override public boolean areItemsTheSame(@NonNull FileSection a, @NonNull FileSection b) {
            return a.header.equals(b.header);
        }
        @Override public boolean areContentsTheSame(@NonNull FileSection a, @NonNull FileSection b) {
            if (!a.header.equals(b.header)) return false;
            if (a.items.size() != b.items.size()) return false;
            if (a.items.isEmpty()) return true;
            CallRecord af = a.items.get(0), bf = b.items.get(0);
            CallRecord al = a.items.get(a.items.size() - 1), bl = b.items.get(b.items.size() - 1);
            boolean firstEq  = af.uri.equals(bf.uri);
            boolean lastEq   = al.uri.equals(bl.uri);
            return firstEq && lastEq;
        }
    };

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_section, parent, false);
        return new VH(v, onCallClick);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(getItem(position));
    }


    static final class VH extends RecyclerView.ViewHolder {
        private final TextView tvHeader;
        private final RecyclerView rvFiles;
        private final FileAdapter child;

        VH(@NonNull View itemView, @Nullable OnCallClick onCallClick) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tv_how_long);
            rvFiles  = itemView.findViewById(R.id.rv_files);
            rvFiles.setItemAnimator(null);
            child = new FileAdapter(uri -> { if (onCallClick != null) onCallClick.onClick(uri); });
            rvFiles.setAdapter(child);
        }

        void bind(@NonNull FileSection section) {
            tvHeader.setText(section.header);
            child.submitList(new ArrayList<>(section.items));
        }
    }
}