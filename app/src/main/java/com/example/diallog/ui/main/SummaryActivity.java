package com.example.diallog.ui.main;

import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.repository.*;
import com.example.diallog.data.repository.cache.CachedTranscriber;
import com.example.diallog.data.repository.cache.FileTranscriptCache;
import com.example.diallog.data.repository.cache.TranscriptCache;
import com.example.diallog.ui.adapter.TranscriptAdapter;
import com.example.diallog.ui.viewmodel.SummaryVMFactory;
import com.example.diallog.ui.viewmodel.SummaryViewModel;

public final class SummaryActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private TranscriptAdapter adapter;
    private SummaryViewModel viewModel;
    private TextView statusView;

    @Override protected void onCreate(@Nullable Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_summary);

        recyclerView = findViewById(R.id.rv_transcript);
        statusView = findViewById(R.id.tv_loading);

        adapter = new TranscriptAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(null);

        Transcriber transcriber = TranscriberProvider.buildTranscriber();
        TranscriptCache tc = new FileTranscriptCache(this, 200);
        Transcriber finalTranscriber = new CachedTranscriber(transcriber, tc);

        viewModel = new ViewModelProvider(
                this, new SummaryVMFactory(finalTranscriber)
        ).get(SummaryViewModel.class);

        adapter.submitList(java.util.Collections.emptyList());
        viewModel.segments().observe(this, adapter::submitList);
        viewModel.error().observe(this, e -> { if (e != null) Toast.makeText(this, e, Toast.LENGTH_SHORT).show(); });

        Uri audioUri = getIntent().getParcelableExtra("audioUri");
        if (audioUri != null) viewModel.transcribe(audioUri);
    }

}
