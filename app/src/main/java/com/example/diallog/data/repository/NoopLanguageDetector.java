package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class NoopLanguageDetector implements LanguageDetector {
    @Override public @NonNull DetectResult detectFromText(@Nullable String text) {
        return DetectResult.KO;
    }
}