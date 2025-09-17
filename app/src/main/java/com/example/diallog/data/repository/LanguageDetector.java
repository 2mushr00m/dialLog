package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

public interface LanguageDetector {
    @NonNull
    Optional<String> detect(@Nullable String text);
}