package com.example.diallog.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.diallog.data.repository.Transcriber;
import com.example.diallog.data.repository.TranscriberProvider;

public final class SummaryVMFactory implements ViewModelProvider.Factory {
    private final Application app;
    public SummaryVMFactory(@NonNull Application app) { this.app = app; }

    @NonNull @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!SummaryViewModel.class.isAssignableFrom(modelClass)) {
            throw new IllegalArgumentException("Unsupported VM: " + modelClass.getName());
        }
        Transcriber transcriber = TranscriberProvider.get();
        return (T) new SummaryViewModel(transcriber);
    }
}



//public final class MainVMFactory implements ViewModelProvider.Factory {
//    private final Application app;
//    private final Handler main = new Handler(Looper.getMainLooper());
//
//    public MainVMFactory(@NonNull Application app) {
//        this.app = app;
//    }
//
//
//    @NonNull @Override
//    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
//        if (!MainViewModel.class.isAssignableFrom(modelClass)) {
//            throw new IllegalArgumentException("Unsupported VM: " + modelClass.getName());
//        }
//
//        final WeakReference<MainViewModel>[] vmRef = new WeakReference[]{null};
//
//        Runnable onDataChanged = () -> {
//            MainViewModel vm = vmRef[0] != null ? vmRef[0].get() : null;
//            if (vm != null) {
//                main.post(vm::loadFirstPage);
//            }
//        };
//        CallRepository repo = RepositoryProvider.buildCallRepository(app.getApplicationContext(), onDataChanged);
//        MainViewModel vm = new MainViewModel(app, repo);
//
//        vmRef[0] = new WeakReference<>(vm);
//        return (T) vm;
//    }
//}