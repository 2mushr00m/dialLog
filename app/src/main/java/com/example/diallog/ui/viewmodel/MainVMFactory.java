package com.example.diallog.ui.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.diallog.data.repository.CallRepository;
import com.example.diallog.data.repository.RepositoryProvider;

import com.example.diallog.data.repository.Transcriber;
import com.example.diallog.data.repository.TranscriberProvider;
import com.example.diallog.data.repository.cache.FileTranscriptCache;
import com.example.diallog.data.repository.cache.TranscriptCache;

import java.lang.ref.WeakReference;

public final class MainVMFactory implements ViewModelProvider.Factory {
    private final Application app;
    private final Handler main = new Handler(Looper.getMainLooper());

    public MainVMFactory(@NonNull Application app) {
        this.app = app;
    }

    @SuppressWarnings("unchecked")
    @NonNull @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!MainViewModel.class.isAssignableFrom(modelClass)) {
            throw new IllegalArgumentException("Unsupported VM: " + modelClass.getName());
        }
        Transcriber transcriber = TranscriberProvider.get();

        final WeakReference<MainViewModel>[] vmRef = new WeakReference[]{null};
        Runnable onDataChanged = () -> {
            MainViewModel vm = vmRef[0] != null ? vmRef[0].get() : null;
            if (vm != null) main.post(vm::loadFirstPage);
        };
        CallRepository repo = RepositoryProvider.buildCallRepository(app.getApplicationContext(), onDataChanged);
        TranscriptCache tcache = new FileTranscriptCache(app.getApplicationContext(), 512);

        MainViewModel vm = new MainViewModel(app, repo, tcache, transcriber);

        vmRef[0] = new WeakReference<>(vm);
        return (T) vm;
    }
}
