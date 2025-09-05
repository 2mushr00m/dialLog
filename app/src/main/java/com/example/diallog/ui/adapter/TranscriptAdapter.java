package com.example.diallog.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.utils.TimeFormatter;

public final class TranscriptAdapter extends ListAdapter<TranscriptSegment, TranscriptAdapter.VH> {
    public TranscriptAdapter() {
        super(new DiffUtil.ItemCallback<>() {
            @Override public boolean areItemsTheSame(@NonNull TranscriptSegment oldItem, @NonNull  TranscriptSegment newItem) {
                return oldItem.startMs == newItem.startMs
                        && oldItem.endMs == newItem.endMs;
            }

            @Override public boolean areContentsTheSame(@NonNull  TranscriptSegment oldItem, @NonNull  TranscriptSegment newItem) {
                return oldItem.startMs == newItem.startMs
                        && oldItem.endMs == newItem.endMs
                        && oldItem.text.equals(newItem.text);
            }
        });
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView start, text;
        VH(View v){
            super(v);
            start = v.findViewById(R.id.start);
            text = v.findViewById(R.id.text);
        }
    }

    @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transcript, parent, false));
    }
    @Override public void onBindViewHolder(VH h, int position){
        TranscriptSegment segment = getItem(position);
        h.start.setText(TimeFormatter.toMmSs(segment.startMs));
        h.text.setText(segment.text);
    }

}
