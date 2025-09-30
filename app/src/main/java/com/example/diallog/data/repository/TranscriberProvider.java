package com.example.diallog.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.R;
import com.example.diallog.auth.GoogleOauth;
import com.example.diallog.config.AppConfig;
import com.example.diallog.data.network.ApiClient;
import com.example.diallog.data.repository.cache.CachedTranscriber;
import com.example.diallog.data.repository.cache.FileTranscriptCache;
import com.example.diallog.data.repository.cache.TranscriptCache;


public final class TranscriberProvider {
    private static final String TAG = "STT Provider";
    private static volatile boolean initialized = false;
    private static volatile @Nullable Transcriber INSTANCE;
    private static Transcriber mock;
    private static Transcriber clova;
    private static GoogleOauth oauth;
    private static GoogleTranscriber google;
    private static LanguageDetector detector;
    private static TranscriptCache cache;

    public static synchronized void init(@NonNull Context ctx) {
        if (initialized) return;
        Context app = ctx.getApplicationContext();

        cache = new FileTranscriptCache(app, 256);
        mock = new MockTranscriber();
        clova = new ClovaSpeechTranscriber(app, ApiClient.clova(), mock);
        try {
            oauth = new GoogleOauth(app, R.raw.service_account);
            google = new GoogleTranscriber(app, ApiClient.google(), oauth, clova);
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

    public static boolean isReady() {
        return initialized;
    }

    @NonNull
    public static synchronized Transcriber get() {
        if (!initialized) {
            throw new IllegalStateException("TranscriberProvider.init(context)를 먼저 호출해 주세요.");
        }
        if (INSTANCE != null) return INSTANCE;

        Transcriber base;
        switch (AppConfig.get().sttRouteMode()) {
            case ROUTE_ON:
                if (google != null) {
                    Log.i(TAG, "STT 라우팅 모드: ROUTE_ON");
                    base = new RouterTranscriber(clova, google, detector);
                    break;
                } else {
                    base = clova;
                    Log.w(TAG, "Google 사용 불가: 고정 엔진=CLOVA");
                }
            case ROUTE_OFF:
            default:
                Log.i(TAG, "STT 라우팅 모드: ROUTE_OFF 고정 엔진=" + AppConfig.get().sttFixedEngine());
                base = selectFixedEngine(clova, google);
        }
        INSTANCE = new CachedTranscriber(base, cache);
        return INSTANCE;
    }
    private static Transcriber selectFixedEngine(Transcriber clova, GoogleTranscriber google) {
        switch (AppConfig.get().sttFixedEngine()) {
            case GOOGLE_ONLY: return google;
            case CLOVA_ONLY:
            default:          return clova;
        }
    }

//    static synchronized void resetForTest() {
//        INSTANCE = null;
//        initialized = false;
//    }
}
