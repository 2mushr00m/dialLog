package com.example.diallog.ui.main;
import com.example.diallog.BuildConfig;

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
import com.example.diallog.ui.adapter.TranscriptAdapter;
import com.example.diallog.ui.viewmodel.SummaryVMFactory;
import com.example.diallog.ui.viewmodel.SummaryViewModel;

public final class SummaryActivity extends AppCompatActivity {
    @Override protected void onCreate(@Nullable Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_summary);

        RecyclerView rv = findViewById(R.id.rv_transcript);

        TranscriptAdapter adapter = new TranscriptAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        Transcriber clova = new ClovaSpeechTranscriber(
                this,
                ApiClient.clova(),
                "ko-KR"
        );
        Transcriber google = new GoogleTranscriber();   // 더미, 미구현

        // 감지기(임시)
        LanguageDetector det = new NoopLanguageDetector();
        Transcriber routed = new RouterTranscriber(clova, google, det);

        boolean useReal = BuildConfig.STT_API_KEY != null && !BuildConfig.STT_API_KEY.isEmpty();
        Transcriber finalTranscriber = useReal ? routed : new MockTranscriber();

        SummaryViewModel vm = new ViewModelProvider(
                this, new SummaryVMFactory(finalTranscriber)
        ).get(SummaryViewModel.class);
        String path = getIntent().getStringExtra("audioPath");

        vm.segments().observe(this, adapter::submitList);
        vm.error().observe(this, e -> { if (e != null) Toast.makeText(this, e, Toast.LENGTH_SHORT).show(); });

        if (path != null) vm.transcribe(path);
    }

}
