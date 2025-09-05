package com.example.diallog.ui.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.diallog.data.repository.CallRepository;

public final class MainVMFactory implements ViewModelProvider.Factory {
    private final CallRepository repo;
    public MainVMFactory(@NonNull CallRepository repo){ this.repo = repo; }

    @NonNull @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> cls) {
        if (cls.isAssignableFrom(MainViewModel.class)) {
            return (T) new MainViewModel(repo);
        }
        throw new IllegalArgumentException("Unknown VM: " + cls.getName());
    }
}
