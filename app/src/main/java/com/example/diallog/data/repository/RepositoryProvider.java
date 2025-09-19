package com.example.diallog.data.repository;

import android.content.Context;

import com.example.diallog.config.AppConfig;

public final class RepositoryProvider {
    public static CallRepository buildCallRepository(Context ctx) {
        switch (AppConfig.get().dataSourceMode()) {
            case MOCK:  return new MockCallRepository(ctx);
            case READ:
            default:    return new FileSystemCallRepository(ctx);
        }
    }

}
