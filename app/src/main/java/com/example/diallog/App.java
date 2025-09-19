package com.example.diallog;

import android.app.Application;

import com.example.diallog.config.AppConfig;
import com.example.diallog.data.repository.TranscriberProvider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class App extends Application {
    private static final String TAG = "App";
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        AppConfig.init(getApplicationContext());

        io.execute(() -> {
            try {
                TranscriberProvider.init(getApplicationContext());
            } catch (Throwable t) {
                android.util.Log.w(TAG, "Transcriber 초기화 실패", t);
            }
        });
    }
}
