package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;

public interface LanguageDetector {
    @NonNull
    String detect(@NonNull Uri audioUri);
}
