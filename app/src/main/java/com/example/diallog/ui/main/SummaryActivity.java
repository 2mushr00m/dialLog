package com.example.diallog.ui.main;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.config.AppConfig;
import com.example.diallog.data.repository.*;
import com.example.diallog.data.repository.cache.CachedTranscriber;
import com.example.diallog.data.repository.cache.FileTranscriptCache;
import com.example.diallog.data.repository.cache.TranscriptCache;
import com.example.diallog.ui.adapter.TranscriptAdapter;
import com.example.diallog.ui.viewmodel.SummaryVMFactory;
import com.example.diallog.ui.viewmodel.SummaryViewModel;

public final class SummaryActivity extends AppCompatActivity {
    public static final String EXTRA_URI = "audioUri";

    private SummaryViewModel vm;
    private RecyclerView rv;
    private TranscriptAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle b) {
        super.onCreate(b);

        AppConfig.get().setIntentOverride(getIntent());

        setContentView(R.layout.activity_summary);
        setSupportActionBar(findViewById(R.id.toolbar));

        rv = findViewById(R.id.rv_transcript_section);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TranscriptAdapter(uri -> {
        });
        rv.setAdapter(adapter);

        Transcriber base = TranscriberProvider.buildTranscriber();
        TranscriptCache cache = new FileTranscriptCache(this, 200);
        Transcriber transcriber = new CachedTranscriber(base, cache);

        vm = new ViewModelProvider(this, new SummaryVMFactory(transcriber))
                .get(SummaryViewModel.class);

        vm.segments().observe(this, adapter::submitList);
        vm.loading().observe(this, loading -> {
            if (loading == null) loading = false;
        });
        vm.error().observe(this, e -> {
            if (e != null && !e.isEmpty()) {
                Toast.makeText(this, e, Toast.LENGTH_SHORT).show();
            }
        });

        // 입력 URI 수신
        Uri audioUri = getIntent().getParcelableExtra(EXTRA_URI);
        if (audioUri != null) {
            vm.transcribe(audioUri);
        } else {
//            Toast.makeText(this, R.string.error_no_audio_uri, Toast.LENGTH_SHORT).show();
        }
    }

}
