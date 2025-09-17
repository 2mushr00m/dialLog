package com.example.diallog.ui.main;

import static android.os.FileObserver.CLOSE_WRITE;
import static android.os.FileObserver.CREATE;
import static android.os.FileObserver.MOVED_TO;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
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
import java.util.ArrayList;
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
            "Call", "Recorder", "record", "통화", "녹음", "CallRec", "CallRecord", "DialLog"
    ));
    private static final Set<String> AUDIO_EXT = new HashSet<>(Arrays.asList(
            "m4a", "mp3", "aac", "wav", "3gp", "amr", "ogg", "flac"
    ));

    private final Set<File> watchDirs = new HashSet<>(Arrays.asList(
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "DialLog"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    ));
    private DirWatcher dirWatcher;


    private ActivityResultLauncher<Intent> pickFolderLauncher;

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
    @Override
    protected void onStart() {
        super.onStart();
        registerAudioObserver();
        startWatchingDirs();
        Log.i(TAG, "onStart: refreshing");
    }
    @Override protected void onStop() {
        super.onStop();
        unregisterAudioObserver();
        stopWatchingDirs();
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
        adapter = new CallAdapter(itemUri -> {
            Intent i = new Intent(this, SummaryActivity.class);
            i.putExtra("audioUri", itemUri);
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
            adapter.submitList(items == null ? new ArrayList<>() : new ArrayList<>(items));
            boolean showEmpty = (items == null || items.isEmpty());
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
                if (viewModel != null) {
                    viewModel.setUserDirUri(treeUri);
                    viewModel.refresh();
                }
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

    private void startWatchingDirs() {
        if (dirWatcher != null) return;
        dirWatcher = new DirWatcher(watchDirs, path -> {
            Log.i(TAG, "File change detected -> scanFile: " + path);
            MediaScannerConnection.scanFile(
                    this,
                    new String[]{ path },
                    null,
                    (scannedPath, uri) -> {
                        Log.i(TAG, "scan completed: " + scannedPath + " uri=" + uri);
                        // 메인스레드에서 목록 갱신
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (viewModel != null) viewModel.hardRefresh();
                        });
                    }
            );
        });
        dirWatcher.start();
        Log.i(TAG, "DirWatcher started");
    }

    private void stopWatchingDirs() {
        if (dirWatcher != null) {
            dirWatcher.stop();
            dirWatcher = null;
            Log.i(TAG, "DirWatcher stopped");
        }
    }


    private static final class DirWatcher {
        interface OnPathChanged { void onChanged(String path); }

        private final Set<FileObserver> observers = new HashSet<>();
        private final Handler main = new Handler(Looper.getMainLooper());
        private long lastNotifiedAt = 0L;
        private final long debounceMs = 500L;
        private final OnPathChanged callback;

        DirWatcher(Set<File> dirs, OnPathChanged cb) {
            this.callback = cb;
            for (File dir : dirs) {
                if (dir != null && dir.isDirectory()) {
                    observers.add(new FileObserver(dir.getAbsolutePath(),
                            CREATE | MOVED_TO | CLOSE_WRITE) {
                        @Override public void onEvent(int event, String file) {
                            if (file == null) return;
                            String path = new File(dir, file).getAbsolutePath();
                            long now = System.currentTimeMillis();
                            if (now - lastNotifiedAt < debounceMs) return;
                            lastNotifiedAt = now;
                            main.post(() -> callback.onChanged(path));
                        }
                    });
                }
            }
        }
        void start() { for (FileObserver fo : observers) fo.startWatching(); }
        void stop()  { for (FileObserver fo : observers) fo.stopWatching();  }
    }



}