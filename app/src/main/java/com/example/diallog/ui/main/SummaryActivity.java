package com.example.diallog.ui.main;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.example.diallog.R;

public final class SummaryActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);
    }

    @Override protected void onStart(){
        super.onStart();
        String audioPath = getIntent().getStringExtra("audioPath");
        // TODO: vm.loadFromPath(audioPath);
    }


}
