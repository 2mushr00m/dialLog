package com.example.diallog.ui.main;

import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.repository.MockTranscriber;
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

        SummaryViewModel vm = new ViewModelProvider(
                this, new SummaryVMFactory(new MockTranscriber())
        ).get(SummaryViewModel.class);

        vm.segments().observe(this, adapter::submitList);
        vm.error().observe(this, e -> { if (e != null) Toast.makeText(this, e, Toast.LENGTH_SHORT).show(); });

        String path = getIntent().getStringExtra("audioPath");
        if (path != null) vm.transcribe(path);
    }

}
