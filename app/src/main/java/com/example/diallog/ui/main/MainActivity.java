package com.example.diallog.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
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

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainUI";

    private MainViewModel viewModel;
    private CallAdapter adapter;
    private ContentObserver audioObserver;
    private long lastRefreshAt = 0L;

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyView;
    private Button btnPickFolder;
    private Button btnSeed;

    private boolean loadingMore = false;
    private boolean reachedEnd = false;

    private static final String PREFS = "diallog_prefs";
    private static final String KEY_DIR_URI = "dir_tree_uri";


    private static final Set<String> HINTS = new HashSet<>(Arrays.asList(
            "Call", "Recorder", "record", "통화", "녹음", "CallRec", "CallRecord", "DialLogSamples"
    ));
    private static final Set<String> AUDIO_EXT = new HashSet<>(Arrays.asList(
            "m4a", "mp3", "aac", "wav"
    ));

    private ActivityResultLauncher<Intent> pickFolderLauncher;

    @Override
    protected void onStart() {
        super.onStart();
        registerAudioObserver();

        if (System.currentTimeMillis() - lastRefreshAt > 1500) {
            Log.i(TAG, "onStart: refreshing");
            viewModel.refresh();
            lastRefreshAt = System.currentTimeMillis();
        }
        Log.i(TAG, "onStart: refreshing");
    }
    @Override protected void onStop() {
        super.onStop();
        unregisterAudioObserver();
    }
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        bindViews();
        setupRecycler();
        setupFolderPicker();


        CallRepository repo = new FileSystemCallRepository(
                getApplicationContext(), getSavedDirUri(),
                HINTS, AUDIO_EXT);
        viewModel = new ViewModelProvider(this, new MainVMFactory(repo)).get(MainViewModel.class);

        if (!PermissionHelper.hasReadAudioPermission(this)) {
            PermissionHelper.requestReadAudio(this);
            return; // 콜백에서 이어서 초기화
        } else {
            observeViewmodel();
            viewModel.refresh();
        }
    }

    private void bindViews() {
        recyclerView = findViewById(R.id.rv_calls);
        progressBar = findViewById(R.id.pb_loading);
        emptyView = findViewById(R.id.view_empty);
        btnPickFolder = findViewById(R.id.btn_pickFolder);
        btnSeed = findViewById(R.id.btn_seedSample);

        btnPickFolder.setOnClickListener(v -> openFolderPicker());
        btnSeed.setOnClickListener(v -> {
//            SampleAudioSeeder.seedIfNeeded(this);
            viewModel.refresh(); // 시딩 후 재스캔
        });
    }
    private void setupRecycler() {
        adapter = new CallAdapter(path -> {
            Intent i = new Intent(this, SummaryActivity.class);
            i.putExtra("audioPath", path);
            startActivity(i);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return;
                if (loadingMore || reachedEnd) return;

                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int total = lm.getItemCount();
                int lastVisible = lm.findLastVisibleItemPosition();

                // 끝에서 5개 이내면 로드 트리거
                if (total > 0 && lastVisible >= total - 5) {
                    loadingMore = true;
                    viewModel.loadMore();
                }
            }
        });
    }
    private void observeViewmodel() {
        Log.i(TAG, "observeViewmodel: set observers");
        viewModel.getItems().observe(this, items -> {
            Log.i(TAG, "items changed: " + (items == null ? -1 : items.size()));
            adapter.submitList(items);
            boolean showEmpty = (adapter.getItemCount() == 0) && !Boolean.TRUE.equals(viewModel.getLoading().getValue());
            emptyView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
        });
        viewModel.getLoading().observe(this, isLoading -> {
            Log.i(TAG, "loading=" + isLoading);
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (!isLoading) {
                loadingMore = false;
            }
        });
        viewModel.getEndReached().observe(this, end -> {
            Log.i(TAG, "endReached=" + end);
            reachedEnd = Boolean.TRUE.equals(end);
        });
        viewModel.getError().observe(this, err -> {
            Log.e(TAG, "error=" + err);
            if (err == null) return;
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
        });
    }

    private void registerAudioObserver() {
        if (audioObserver != null) return;
        audioObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange, @Nullable Uri uri) {
                Log.i(TAG, "MediaStore changed uri=" + uri);
                if (System.currentTimeMillis() - lastRefreshAt < 800) return;
                try { viewModel.refresh(); } finally { lastRefreshAt = System.currentTimeMillis(); }
            }
        };
        getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                audioObserver
        );
        Log.i(TAG, "ContentObserver registered");
    }
    private void unregisterAudioObserver() {
        if (audioObserver == null) return;
        getContentResolver().unregisterContentObserver(audioObserver);
        audioObserver = null;
        Log.i(TAG, "ContentObserver unregistered");
    }

    private void setupFolderPicker() {
        pickFolderLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri treeUri = result.getData().getData();
                if (treeUri == null) return;

                getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                saveDirUri(treeUri);

                // repo 교체 후 새로고침
                CallRepository repo = new FileSystemCallRepository(getApplicationContext(), treeUri, HINTS, AUDIO_EXT);
                viewModel.replaceRepository(repo);
                viewModel.refresh();
                Toast.makeText(this, "폴더가 지정되었습니다.", Toast.LENGTH_SHORT).show();
            }
        );
    }
    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        // 다운로드 등 특정 루트로 유도하려면 아래 옵션 사용 가능
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, (Uri) null);
        pickFolderLauncher.launch(intent);
    }

    @Override
    public void onRequestPermissionsResult(int reqCode, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(reqCode, perms, grants);
        if (PermissionHelper.hasReadAudioPermission(this)) {
            if (viewModel == null) {
                CallRepository repo = new FileSystemCallRepository(getApplicationContext(), getSavedDirUri(), HINTS, AUDIO_EXT);
                viewModel = new ViewModelProvider(this, new MainVMFactory(repo)).get(MainViewModel.class);
            }
            observeViewmodel();
            viewModel.refresh();
        } else {
            Toast.makeText(this, "오디오 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveDirUri(@NonNull Uri uri) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        sp.edit().putString(KEY_DIR_URI, uri.toString()).apply();
    }

    @Nullable
    private Uri getSavedDirUri() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String s = sp.getString(KEY_DIR_URI, null);
        return s == null ? null : Uri.parse(s);
    }

}