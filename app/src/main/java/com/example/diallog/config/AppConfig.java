package com.example.diallog.config;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

public final class AppConfig {
    public enum Mode { MOCK, REAL }

    private static final String PREF = "diallog_prefs";
    private static final String KEY_MODE = "app_mode";

    private AppConfig() {}

    /** 현재 모드 조회. 기본값 REAL. */
    @NonNull public static Mode getMode(@NonNull Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String v = sp.getString(KEY_MODE, Mode.REAL.name());
        try { return Mode.valueOf(v); } catch (Exception ignore) { return Mode.REAL; }
    }

    /** 모드 저장. UI에서 토글 시 호출. */
    public static void setMode(@NonNull Context ctx, @NonNull Mode mode) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_MODE, mode.name()).apply();
    }

    public static boolean isMock(@NonNull Context ctx) {
        return getMode(ctx) == Mode.MOCK;
    }
}