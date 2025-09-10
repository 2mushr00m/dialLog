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
        offset = 0;
        endReached.setValue(false);
        items.setValue(new ArrayList<>());
        try { repo.reload(); } catch (Throwable ignore) {}
        loadMore();
    }

    @Override protected void onCleared() { io.shutdownNow(); }

}
