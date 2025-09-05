package com.example.diallog.ui.viewmodel;

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
    private final CallRepository repo;
    private final MutableLiveData<List<CallRecord>> items = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private int offset = 0;
    private int pageSize = 20;
    private boolean hasMore = true;


    public MainViewModel(CallRepository repo) {
        this.repo = repo;
    }

    public LiveData<List<CallRecord>> items()   { return items; }
    public LiveData<Boolean> loading()          { return loading; }
    public LiveData<String> error()             { return error;  }
    public boolean hasMore()                    { return hasMore; }


    public void loadFirst(int limit) {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        pageSize = Math.max(1, limit);
        offset = 0; hasMore = true; error.postValue(null);
        loading.postValue(true);
        io.submit(() -> {
            try {
                List<CallRecord> page = repo.getRecent(0, pageSize);
                items.postValue(new ArrayList<>(page));
                offset = page.size();
                hasMore = page.size() == pageSize;
            } catch (Exception e) {
                error.postValue(e.getMessage());
            } finally {
                loading.postValue(false);
            }
        });
    }

    public void loadMore(){
        if (Boolean.TRUE.equals(loading.getValue())) return;
        if (!hasMore) return;
        loading.postValue(true);
        io.submit(() -> {
            try {
                List<CallRecord> page = repo.getRecent(offset, pageSize);
                List<CallRecord> current = items.getValue();
                if (current == null) current = new ArrayList<>();
                current = new ArrayList<>(current);
                current.addAll(page);
                items.postValue(current);
                offset += page.size();
                hasMore = page.size() == pageSize;
            } catch (Exception e) {
                error.postValue(e.getMessage());
            } finally {
                loading.postValue(false);
            }
        });
    }

    @Override protected void onCleared() { io.shutdownNow(); }

}
