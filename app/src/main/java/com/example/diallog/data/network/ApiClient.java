package com.example.diallog.data.network;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.diallog.R;
import com.example.diallog.auth.AuthTokenProvider;
import com.example.diallog.auth.GoogleOauth;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import com.example.diallog.BuildConfig;

public final class ApiClient {
    public static Retrofit clova() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        OkHttpClient ok = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
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
        log.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        OkHttpClient ok = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(log)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BuildConfig.GOOGLE_STT_BASE)
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
