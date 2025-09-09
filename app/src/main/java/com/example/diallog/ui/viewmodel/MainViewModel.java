package com.example.diallog.ui.viewmodel;

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
    private static final int PAGE_SIZE = 20;

    private final CallRepository repo;
    private final MutableLiveData<List<CallRecord>> items = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isEmpty = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);

    private boolean endReached = false;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private int offset = 0;


    public MainViewModel(CallRepository repo) {
        this.repo = repo;
    }

    public LiveData<List<CallRecord>> getItems() { return items; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<Boolean> getIsEmpty() { return isEmpty; }
    public LiveData<String> getError() { return error; }
    public boolean isEndReached() { return endReached; }


    @MainThread public void loadMore(){
        if (Boolean.TRUE.equals(loading.getValue()) || endReached) return;
        loading.setValue(true);
        error.setValue(null);

        io.submit(() -> {
            try {
                List<CallRecord> page = repo.getRecent(offset, PAGE_SIZE);

                if (page == null || page.isEmpty()) {
                    endReached = true;
                    List<CallRecord> cur = items.getValue();
                    boolean currentlyEmpty = (cur == null || cur.isEmpty());
                    if (currentlyEmpty) isEmpty.postValue(true);
                } else {
                    List<CallRecord> current = new ArrayList<>(items.getValue());
                    current.addAll(page);
                    items.postValue(current);
                    offset += page.size();
                    if (page.size() < PAGE_SIZE) endReached = true;
                    isEmpty.postValue(current.isEmpty());
                }
            } catch (Exception ex) {
                error.postValue(ex.getMessage());
            } finally {
                loading.postValue(false);
            }
        });
    }

    @MainThread
    public void refresh() {
        offset = 0;
        endReached = false;
        isEmpty.setValue(false);
        items.setValue(new ArrayList<>());
        loadMore();
    }

    @Override protected void onCleared() { io.shutdownNow(); }

}
