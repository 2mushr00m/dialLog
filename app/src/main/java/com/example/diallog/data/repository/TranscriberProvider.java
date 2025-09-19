package com.example.diallog.data.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.diallog.App;
import com.example.diallog.R;
import com.example.diallog.auth.AuthTokenProvider;
import com.example.diallog.auth.GoogleOauth;
import com.example.diallog.config.AppConfig;
import com.example.diallog.data.network.ApiClient;
import com.example.diallog.data.repository.cache.CachedTranscriber;
import com.example.diallog.utils.MlKitLanguageDetector;

import retrofit2.Retrofit;

public final class TranscriberProvider {
    private static final String TAG = "STT Provider";
    private static volatile boolean initialized = false;
    private static GoogleOauth oauth;
    private static Transcriber clova;
    private static GoogleTranscriber google;
    private static LanguageDetector detector;

    public static synchronized void init(@NonNull Context ctx) {
        if (initialized) return;

        clova = new ClovaSpeechTranscriber(ctx, ApiClient.clova());
        try {
            oauth = new GoogleOauth(ctx, R.raw.service_account);
            google = new GoogleTranscriber(ctx, ApiClient.google(), oauth);
        } catch (Exception e) {
            Log.w(TAG, "Google 생성 실패: CLOVA_ONLY로 엔진 고정", e);
            google = null;
            AppConfig.get().setSttRouteMode(AppConfig.SttRouteMode.ROUTE_OFF);
            AppConfig.get().setSttFixedEngine(AppConfig.SttFixedEngine.CLOVA_ONLY);
        }

        try {
            detector = new MlKitLanguageDetector();
        } catch (Throwable t) {
            Log.w(TAG, "Ml Kit 생성 실패: ko-KR로 언어 고정", t);
            detector = new NoopLanguageDetector();
        }

        initialized = true;
    }

    public static Transcriber buildTranscriber() {
        Transcriber base;
        switch (AppConfig.get().sttRouteMode()) {
            case ROUTE_ON:
                Log.i(TAG, "STT 라우팅 모드: ROUTE_ON");
                base = new RouterTranscriber(clova, google, detector);
                break;
            case ROUTE_OFF:
            default:
                Log.i(TAG, "STT 라우팅 모드: ROUTE_OFF 고정 엔진=" + AppConfig.get().sttFixedEngine());
                base = selectFixedEngine(clova, google);
        }
        return new CachedTranscriber(base);
    }

    private static Transcriber selectFixedEngine(Transcriber clova, GoogleTranscriber google) {
        switch (AppConfig.get().sttFixedEngine()) {
            case GOOGLE_ONLY: return google;
            case CLOVA_ONLY:
            default:          return clova;
        }
    }
}
