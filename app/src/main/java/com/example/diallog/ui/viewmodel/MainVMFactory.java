package com.example.diallog.ui.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.diallog.data.repository.CallRepository;
import com.example.diallog.data.repository.RepositoryProvider;

import java.lang.ref.WeakReference;

public final class MainVMFactory implements ViewModelProvider.Factory {
    private final Application app;
    private final Handler main = new Handler(Looper.getMainLooper());

    public MainVMFactory(@NonNull Application app) {
        this.app = app;
    }


    @NonNull @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!MainViewModel.class.isAssignableFrom(modelClass)) {
            throw new IllegalArgumentException("Unsupported VM: " + modelClass.getName());
        }

        final WeakReference<MainViewModel>[] vmRef = new WeakReference[]{null};

        Runnable onDataChanged = () -> {
            MainViewModel vm = vmRef[0] != null ? vmRef[0].get() : null;
            if (vm != null) {
                main.post(vm::loadFirstPage);
            }
        };
        CallRepository repo = RepositoryProvider.buildCallRepository(app.getApplicationContext(), onDataChanged);
        MainViewModel vm = new MainViewModel(app, repo);

        vmRef[0] = new WeakReference<>(vm);
        return (T) vm;
    }
}
