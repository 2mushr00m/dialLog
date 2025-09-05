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
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<CallRecord>> _items = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _error = new MutableLiveData<>(null);

    private int offset = 0;
    private int pageSize = 20;


    public MainViewModel(CallRepository repo) {
        this.repo = repo;
    }

    public LiveData<List<CallRecord>> items() { return _items; }
    public LiveData<Boolean> loading() { return _loading; }
    public LiveData<String> error() { return _error; }


    public void loadFirst(int pageSize) {
        this.pageSize = pageSize;
        offset = 0;
        _items.postValue(new ArrayList<>());
        loadMore();
    }

    public void loadMore() {
        if (Boolean.TRUE.equals(_loading.getValue())) return;
        _loading.postValue(true);
        final int start = offset;
        io.execute(() -> {
            try {
                List<CallRecord> page = repo.getRecent(start, pageSize);
                List<CallRecord> current = _items.getValue();
                if (current == null) current = new ArrayList<>();
                List<CallRecord> merged = new ArrayList<>(current);
                merged.addAll(page);
                _items.postValue(merged);
                offset = start + page.size();
                _error.postValue(null);
            } catch (Exception e) {
                _error.postValue(e.getMessage());
            } finally {
                _loading.postValue(false);
            }
        });
    }

    public void refresh() { loadFirst(pageSize); }

    @Override protected void onCleared() {
        io.shutdownNow();
    }

}
