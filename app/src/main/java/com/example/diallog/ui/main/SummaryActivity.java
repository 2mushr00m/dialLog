package com.example.diallog.ui.main;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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
import com.example.diallog.ui.adapter.TranscriptSectionAdapter;
import com.example.diallog.ui.viewmodel.MainVMFactory;
import com.example.diallog.ui.viewmodel.MainViewModel;
import com.example.diallog.ui.viewmodel.SummaryVMFactory;
import com.example.diallog.ui.viewmodel.SummaryViewModel;

public final class SummaryActivity extends AppCompatActivity {

    public static final String EXTRA_URI = "audioUri";
    private SummaryViewModel vm;
    private RecyclerView rv;
    private TranscriptSectionAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle b) {
        super.onCreate(b);

        AppConfig.get().setIntentOverride(getIntent());

        setContentView(R.layout.activity_summary);
        setSupportActionBar(findViewById(R.id.toolbar));

        rv = findViewById(R.id.rv_sections);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setItemAnimator(null);
        adapter = new TranscriptSectionAdapter();
        rv.setAdapter(adapter);

        vm = new ViewModelProvider(
                this, new SummaryVMFactory(getApplication())
        ).get(SummaryViewModel.class);

        vm.sections().observe(this, sections -> {
            if (sections != null) adapter.submitList(sections);
        });
        vm.loading().observe(this, loading -> {
            if (loading == null) loading = false;
        });
        vm.error().observe(this, e -> {
            if (e != null && !e.isEmpty()) {
                Toast.makeText(this, e, Toast.LENGTH_SHORT).show();
            }
        });

        Uri audioUri = getIntent().getParcelableExtra(SummaryActivity.EXTRA_URI);
        if (audioUri != null) {
            vm.transcribe(audioUri);
        } else {
            // Toast.makeText(this, R.string.error_no_audio_uri, Toast.LENGTH_SHORT).show();
        }
    }

}
