package com.example.diallog.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.diallog.data.repository.Transcriber;
import com.example.diallog.data.repository.TranscriberProvider;
import com.example.diallog.data.repository.cache.FileTranscriptCache;
import com.example.diallog.data.repository.cache.TranscriptCache;

public final class SummaryVMFactory implements ViewModelProvider.Factory {
    private final Application app;
    public SummaryVMFactory(@NonNull Application app) { this.app = app; }

    @SuppressWarnings("unchecked")
    @NonNull @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!SummaryViewModel.class.isAssignableFrom(modelClass)) {
            throw new IllegalArgumentException("Unsupported VM: " + modelClass.getName());
        }
        Transcriber transcriber = TranscriberProvider.get();
        TranscriptCache tcache = new FileTranscriptCache(app.getApplicationContext(), /*maxEntries*/ 512);
        return (T) new SummaryViewModel(transcriber, tcache);
    }
}