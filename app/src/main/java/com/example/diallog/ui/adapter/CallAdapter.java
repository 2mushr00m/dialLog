package com.example.diallog.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.model.CallRecord;
import com.example.diallog.utils.TimeFormatter;

import java.util.ArrayList;
import java.util.List;

public final class CallAdapter extends RecyclerView.Adapter<CallAdapter.CallViewHolder> {

    public interface OnClick { void onCallClick(String path); }
    private final OnClick onClick;
    private final List<CallRecord> items = new ArrayList<>();
    public CallAdapter(OnClick onClick){
        this.onClick = onClick;
    }

    public void submitList(@NonNull List<CallRecord> data){
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_call, parent, false);
        return new CallViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        CallRecord item = items.get(position);
        holder.bind(item);
        holder.itemView.setOnClickListener(v -> onClick.onCallClick(item.path));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class CallViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvSub;

        CallViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSub = itemView.findViewById(R.id.tv_sub);
        }

        void bind(@NonNull CallRecord item) {
            tvTitle.setText(item.fileName);
            tvSub.setText(TimeFormatter.toMmSs(item.durationMs));
        }
    }


}