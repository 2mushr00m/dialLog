package com.example.diallog.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.diallog.config.AppConfig;

public final class RepositoryProvider {
    private RepositoryProvider() {}

    @NonNull
    public static CallRepository buildCallRepository(@NonNull Context ctx, @NonNull Runnable onDataChanged) {
        switch (AppConfig.get().dataSourceMode()) {
            case MOCK:
                return new MockCallRepository(ctx.getApplicationContext());
            case READ:
            default:
                return new FileSystemCallRepository(ctx.getApplicationContext(), onDataChanged);
        }
    }

}
