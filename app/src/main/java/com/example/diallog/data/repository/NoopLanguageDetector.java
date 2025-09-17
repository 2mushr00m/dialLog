package com.example.diallog.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

public final class NoopLanguageDetector implements LanguageDetector {

    @Override
    public @NonNull Optional<String> detect(@Nullable String text) {
        return Optional.empty();
    }
}