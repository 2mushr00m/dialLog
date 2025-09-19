package com.example.diallog.config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

public final class AppConfig {
    public enum DataSourceMode { MOCK, READ }
    public enum SttRouteMode { ROUTE_ON, ROUTE_OFF }
    public enum SttFixedEngine { AUTO, CLOVA_ONLY, GOOGLE_ONLY }


    private static final String PREFS = "app_config";
    private static final String K_DATA_MODE = "dataMode";
    private static final String K_STT_ROUTE = "sttRoute";
    private static final String K_STT_FIXED = "sttFixed";

    private static volatile AppConfig INSTANCE;

    public static void init(Context appCtx) {
        if (INSTANCE == null) {
            synchronized (AppConfig.class) {
                if (INSTANCE == null)
                    INSTANCE = new AppConfig(appCtx.getApplicationContext());
            }
        }
    }
    public static AppConfig get() {
        if (INSTANCE == null)
            throw new IllegalStateException("Call AppConfig.init(context) First.");
        return INSTANCE;
    }

    private final SharedPreferences prefs;
    private @Nullable Intent intentOverride;
    private AppConfig(Context appCtx) {
        this.prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    public void setIntentOverride(@Nullable Intent launchIntent) {
        this.intentOverride = launchIntent;
    }

    public DataSourceMode dataSourceMode() {
        String p = prefs.getString(K_DATA_MODE, null);
        return parseEnum(p, DataSourceMode.class, DataSourceMode.READ); // 기본 READ
    }
    public SttRouteMode sttRouteMode() {
        String p = prefs.getString(K_STT_ROUTE, null);
        return parseEnum(p, SttRouteMode.class, SttRouteMode.ROUTE_OFF); // 기본 OFF
    }
    public SttFixedEngine sttFixedEngine() {
        if (sttRouteMode() == SttRouteMode.ROUTE_ON) return SttFixedEngine.AUTO;
        String p = prefs.getString(K_STT_FIXED, null);
        return parseEnum(p, SttFixedEngine.class, SttFixedEngine.CLOVA_ONLY); // 기본 CLOVA_ONLY
    }

    public void setSttRouteMode(SttRouteMode m) { prefs.edit().putString(K_STT_ROUTE, m.name()).apply(); }
    public void setSttFixedEngine(SttFixedEngine e) { prefs.edit().putString(K_STT_FIXED, e.name()).apply(); }
    public void reset() { prefs.edit().clear().apply(); }

    private static <E extends Enum<E>> E parseEnum(@Nullable String v, Class<E> t, E def) {
        if (v == null) return def;
        try { return Enum.valueOf(t, v); }
        catch (IllegalArgumentException e) { return def; }
    }
}