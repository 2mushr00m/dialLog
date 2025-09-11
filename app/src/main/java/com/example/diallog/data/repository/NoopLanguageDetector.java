package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;

public final class NoopLanguageDetector implements LanguageDetector {
    @Override public @NonNull String detect(@NonNull Uri audioUri) { return "ko-KR"; }
}