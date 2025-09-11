package com.example.diallog.ui.viewmodel;

import android.util.Log;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.diallog.data.model.CallRecord;
import com.example.diallog.data.repository.CallRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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


    public void replaceRepository(CallRepository repo) {
    }
    @MainThread public void refresh() {
        Log.i(TAG, "refresh: reset");
        if (Boolean.TRUE.equals(loading.getValue())) {
            pendingRefresh = true;
            return;
        }
        io.submit(() -> {
            try {
                if (!initialized) { // 최초 진입시에만 스캔
                    if (repo instanceof com.example.diallog.data.repository.FileSystemCallRepository) {
                        ((com.example.diallog.data.repository.FileSystemCallRepository) repo).ensureScanned();
                    } else {
                        // CallRepository가 다른 구현이어도 예외 없이 진행
                    }
                    initialized = true;
                }
                // 페이징 리셋
                offset = 0;
                endReached.postValue(false);
                items.postValue(new ArrayList<>());
                // 첫 페이지 적재
                List<CallRecord> page = repo.getRecent(0, PAGE_SIZE);
                items.postValue(page);
                offset = page.size();
                if (page.size() < PAGE_SIZE) endReached.postValue(true);
            } catch (Exception ex) {
                Log.e(TAG, "refresh(soft): error", ex);
                error.postValue(ex.getMessage());
            }
        });
    }

    @MainThread public void hardRefresh() { // 하드 리프레시: 외부 변경 시 전체 재스캔
        Log.i(TAG, "hardRefresh: force rescan");
        if (Boolean.TRUE.equals(loading.getValue())) { pendingRefresh = true; return; }

        io.submit(() -> {
            try {
                repo.reload(); // Repo에서 즉시 재스캔 수행
                offset = 0;
                endReached.postValue(false);
                items.postValue(new ArrayList<>());
                List<CallRecord> page = repo.getRecent(0, PAGE_SIZE);
                items.postValue(page);
                offset = page.size();
                if (page.size() < PAGE_SIZE) endReached.postValue(true);
            } catch (Exception ex) {
                Log.e(TAG, "hardRefresh: error", ex);
                error.postValue(ex.getMessage());
            }
        });
    }

    @Override protected void onCleared() { io.shutdownNow(); }

}
