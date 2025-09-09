package com.example.diallog.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

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
import com.example.diallog.utils.PermissionHelper;


public final class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private CallAdapter adapter;
    private MainViewModel viewModel;
    private ProgressBar progressBar;
    private View emptyView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.rv_calls);
        progressBar = findViewById(R.id.pb_loading);
        emptyView = findViewById(R.id.view_empty);

        adapter = new CallAdapter(path -> {
            Intent i = new Intent(this, SummaryActivity.class);
            i.putExtra("audioPath", path);
            startActivity(i);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(null);

        if (!PermissionHelper.hasReadAudioPermission(this)) {
            PermissionHelper.requestReadAudio(this);
            return; // 콜백에서 이어서 초기화
        }
        initViewModelAndObservers();
    }

    private void initViewModelAndObservers() {
//        CallRepository repo = new MockCallRepository(getApplicationContext());
        CallRepository repo = new FileSystemCallRepository(this);
        viewModel = new ViewModelProvider(this, new MainVMFactory(repo)).get(MainViewModel.class);

        adapter.submitList(java.util.Collections.emptyList());

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;

                int last = lm.findLastVisibleItemPosition();
                if (last == RecyclerView.NO_POSITION) return;
                if (adapter.getItemCount() == 0) return;
                if (!viewModel.isEndReached() && last >= adapter.getItemCount() - 3) {
                    viewModel.loadMore();
                }
            }
        });

        viewModel.getItems().observe(this, adapter::submitList);
        viewModel.getLoading().observe(this,
                isLoading -> progressBar.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE));
        viewModel.getError().observe(this,
                msg -> {
                    if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                });

        viewModel.getIsEmpty().observe(this, empty -> {
            if (emptyView == null) return;
            emptyView.setVisibility(Boolean.TRUE.equals(empty) ? View.VISIBLE : View.GONE);
        });

        viewModel.refresh();
    }

    @Override
    public void onRequestPermissionsResult(int reqCode, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(reqCode, perms, res);
        if (reqCode == PermissionHelper.REQ_READ_AUDIO) {
            if (PermissionHelper.hasReadAudioPermission(this)) {
                initViewModelAndObservers();
            } else {
//                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                finish(); // 정책대로 종료 또는 대체 처리
            }
        }
    }
}