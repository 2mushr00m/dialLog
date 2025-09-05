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

import java.util.ArrayList;
import java.util.List;

public final class TranscriptAdapter extends RecyclerView.Adapter<TranscriptAdapter.VH> {
    private final List<TranscriptSegment> items = new ArrayList<>();

    public void submitList(@NonNull List<TranscriptSegment> data){
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v){
        View view = LayoutInflater.from(p.getContext()).inflate(R.layout.item_transcript, p, false);
        return new VH(view);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos){
        TranscriptSegment s = items.get(pos);
        h.time.setText(TimeFormatter.toMmSs(s.startMs));
        h.text.setText(s.text);
    }

    @Override public int getItemCount(){ return items.size(); }

    static final class VH extends RecyclerView.ViewHolder{
        final TextView time, text;
        VH(@NonNull View itemView){
            super(itemView);
            text = itemView.findViewById(R.id.tv_text);
            time = itemView.findViewById(R.id.tv_time);
        }
    }
}
