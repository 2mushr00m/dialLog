package com.example.diallog.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.repository.CallRepository;
import com.example.diallog.data.repository.FileSystemCallRepository;
import com.example.diallog.data.repository.MockCallRepository;
import com.example.diallog.ui.adapter.CallAdapter;
import com.example.diallog.ui.viewmodel.MainVMFactory;
import com.example.diallog.ui.viewmodel.MainViewModel;

import java.io.File;
import java.util.Objects;


public final class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private CallAdapter adapter;
    private boolean loading = false;
    private int loaded = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

//        File recordings = new File(
//                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
//                "Recordings"
//        );
        CallRepository repo = new MockCallRepository();
//        CallRepository repo = new FileSystemCallRepository(recordings);
        MainViewModel vm = new ViewModelProvider(this, new MainVMFactory(repo)).get(MainViewModel.class);




        CallAdapter adapter = new CallAdapter(path -> {
            Intent i = new Intent(this, SummaryActivity.class);
            i.putExtra("audioPath", path);
            startActivity(i);
        });

        recyclerView = findViewById(R.id.rv_calls);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        vm.items().observe(this, adapter::submitList);
//        vm.loading().observe(this, isLoading -> progressBar.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE));
//        vm.error().observe(this, msg -> { if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); });

        vm.loadFirst(20);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int last = lm.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 3 && vm.hasMore()) vm.loadMore();
            }
        });
    }

}
