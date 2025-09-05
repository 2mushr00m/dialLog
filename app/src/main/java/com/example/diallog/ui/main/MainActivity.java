package com.example.diallog.ui.main;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.ui.adapter.CallAdapter;

import java.util.Objects;


public final class MainActivity extends AppCompatActivity {
    private RecyclerView rv;
    private CallAdapter adapter;
    private boolean loading = false;
    private int loaded = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        adapter = new CallAdapter(this::onCallClick);

        rv = findViewById(R.id.rv_calls);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
    }

    private void onCallClick(String path){
        Intent i = new Intent(this, SummaryActivity.class);
        i.putExtra("audioPath", path);
        startActivity(i);
    }

    private void attachPaging() {
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView v, int dx, int dy){
                if (dy <= 0) return;

                LinearLayoutManager lm=(LinearLayoutManager)v.getLayoutManager();
                int last = Objects.requireNonNull(lm).findLastVisibleItemPosition();
                if (!loading && last >= adapter.getItemCount()-4) requestMore();
            }
        });
    }

    private void requestMore() {
        loading = true;
        // TODO: MainViewModel.loadMore(loaded, 20);
        // 완료 시 loaded += 20; loading = false; adapter.submitList(...)
    }
}
