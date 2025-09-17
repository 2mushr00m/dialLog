package com.example.diallog.data.network;


import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import com.example.diallog.BuildConfig;

public final class ApiClient {
    private static final long CONNECT_TIMEOUT_SECONDS = 30L;
    private static final long READ_TIMEOUT_SECONDS = 120L;

    public static Retrofit clova() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        OkHttpClient ok = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(log)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BuildConfig.NAVER_CLOVA_STT_BASE)
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static Retrofit google() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient ok = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(log)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BuildConfig.GOOGLE_STT_BASE)
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
