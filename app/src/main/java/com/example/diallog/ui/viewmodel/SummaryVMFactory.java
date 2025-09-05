package com.example.diallog.ui.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.diallog.data.repository.Transcriber;

public final class SummaryVMFactory implements ViewModelProvider.Factory {
    private final Transcriber transcriber;
    public SummaryVMFactory(@NonNull Transcriber t){ this.transcriber = t; }

    @NonNull @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> cls) {
        if (cls.isAssignableFrom(SummaryViewModel.class)) {
            return (T) new SummaryViewModel(transcriber);
        }
        throw new IllegalArgumentException("Unknown VM: " + cls.getName());
    }
}