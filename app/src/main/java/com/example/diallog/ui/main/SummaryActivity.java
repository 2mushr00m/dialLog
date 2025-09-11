package com.example.diallog.ui.main;
import com.example.diallog.BuildConfig;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.network.ApiClient;
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

    @Override protected void onCreate(@Nullable Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_summary);

        recyclerView = findViewById(R.id.rv_transcript);

        adapter = new TranscriptAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(null);

        Transcriber clova = new ClovaSpeechTranscriber(
                this,
                ApiClient.clova(),
                "ko-KR"
        );
        Transcriber google = new GoogleTranscriber();   // 더미, 미구현

        // 감지기(임시)
        LanguageDetector det = new NoopLanguageDetector();
        Transcriber routed = new RouterTranscriber(clova, google, det);
        // Transcriber base = useReal ? routed : new MockTranscriber();
        TranscriptCache tc = new FileTranscriptCache(this, 200);
        Transcriber finalTranscriber = new CachedTranscriber(routed, tc);

        viewModel = new ViewModelProvider(
                this, new SummaryVMFactory(finalTranscriber)
        ).get(SummaryViewModel.class);
        String path = getIntent().getStringExtra("audioPath");

        adapter.submitList(java.util.Collections.emptyList());
        viewModel.segments().observe(this, adapter::submitList);
        viewModel.error().observe(this, e -> { if (e != null) Toast.makeText(this, e, Toast.LENGTH_SHORT).show(); });

        Uri audioUri = getIntent().getParcelableExtra("audioUri");
        if (audioUri != null) viewModel.transcribe(audioUri);
    }

}
