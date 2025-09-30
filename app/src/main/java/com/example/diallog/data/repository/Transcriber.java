package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.TranscriberResult;


public interface Transcriber {
    @NonNull
    TranscriberResult transcribe(@NonNull Uri audioUri);

    default @NonNull TranscriberResult transcribe(@NonNull Uri audioUri, @NonNull String languageCode) {
        return transcribe(audioUri);
    }
}