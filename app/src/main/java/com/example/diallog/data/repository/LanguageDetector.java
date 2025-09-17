package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface LanguageDetector {
    enum DetectResult {
        KO,
        NON_KO,
        UNKNOWN
    }

    @NonNull DetectResult detectFromText(@Nullable String text);
}