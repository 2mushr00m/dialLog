package com.example.diallog.ui.viewmodel;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.diallog.data.model.CallRecord;
import com.example.diallog.data.repository.CallRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainViewModel extends ViewModel {
    private static final String TAG = "MainVM";

    private static final int PAGE_SIZE = 20;

    private final CallRepository repo;
    private final MutableLiveData<List<CallRecord>> items = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private MutableLiveData<Boolean> endReached = new MutableLiveData<>(false);
    private boolean initialized = false;


    private volatile boolean pendingRefresh = false;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private int offset = 0;


    public MainViewModel(CallRepository repo) {
        this.repo = repo;
    }

    public LiveData<List<CallRecord>> getItems() { return items; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getEndReached() { return endReached; }


    @MainThread public void loadMore(){
        if (Boolean.TRUE.equals(loading.getValue()) || Boolean.TRUE.equals(endReached.getValue())) {
            Log.i(TAG, "loadMore: skipped loading=" + loading.getValue() + " end=" + endReached.getValue());
            return;
        }
        Log.i(TAG, "loadMore: start offset=" + offset + " page=" + PAGE_SIZE);
        loading.setValue(true);
        error.setValue(null);

        io.submit(() -> {
            try {
                List<CallRecord> page = repo.getRecent(offset, PAGE_SIZE);

                if (page == null || page.isEmpty()) {
                    endReached.postValue(true);
                } else {
                    List<CallRecord> current = new ArrayList<>(items.getValue()!= null ? items.getValue() : new ArrayList<>());
                    current.addAll(page);
                    items.postValue(current);
                    offset += page.size();
                    if (page.size() < PAGE_SIZE)
                        endReached.postValue(true);
                }
            } catch (Exception ex) {
                Log.e(TAG, "loadMore: error", ex);
                error.postValue(ex.getMessage());
            } finally {
                loading.postValue(false);
                Log.i(TAG, "loadMore: done offset=" + offset);
                if (pendingRefresh) {
                    pendingRefresh = false;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(this::refresh);
                }
            }
        });
    }


    public void setUserDirUri(@Nullable Uri uri) { repo.setUserDirUri(uri); }
    @MainThread public void refresh() {
        Log.i(TAG, "refresh: reset");
        if (Boolean.TRUE.equals(loading.getValue())) {
            pendingRefresh = true;
            return;
        }
        io.submit(() -> executeRefreshPipeline("refresh"));
    }

    @MainThread public void hardRefresh() { // 하드 리프레시: 외부 변경 시 전체 재스캔
        Log.i(TAG, "hardRefresh: force rescan");
        if (Boolean.TRUE.equals(loading.getValue())) { pendingRefresh = true; return; }

        io.submit(() -> executeRefreshPipeline("hardRefresh"));
    }

    @Override protected void onCleared() { io.shutdownNow(); }

    private void executeRefreshPipeline(@NonNull String reason) {
        Throwable scanError = null;
        try {
            Future<?> future = repo.refreshAsync();
            if (future != null) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    scanError = e.getCause() != null ? e.getCause() : e;
                    Log.e(TAG, reason + ": scan failed", scanError);
                }
            } else {
                repo.ensureScanned();
            }

            offset = 0;
            endReached.postValue(false);
            items.postValue(new ArrayList<>());
            List<CallRecord> page = repo.getRecent(0, PAGE_SIZE);
            items.postValue(page);
            offset = page.size();
            if (page.size() < PAGE_SIZE) endReached.postValue(true);

            if (scanError != null) {
                error.postValue(scanError.getMessage());
            }
        } catch (Exception ex) {
            Log.e(TAG, reason + ": error", ex);
            error.postValue(ex.getMessage());
        }
    }

}
