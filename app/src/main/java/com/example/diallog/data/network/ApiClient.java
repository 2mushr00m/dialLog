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

        Interceptor peekJson = chain -> {
            okhttp3.Response r = chain.proceed(chain.request());
            String ct = r.header("Content-Type", "");
            if (ct != null && ct.contains("application/json")) {
                okhttp3.ResponseBody peek = r.peekBody(512 * 1024);
                Log.d("ClovaJSON", peek.string());
            }
            return r;
        };

        OkHttpClient ok = new OkHttpClient.Builder()
                .addInterceptor(peekJson)
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

    public static Retrofit google(Context app) {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.HEADERS);
//        AuthTokenProvider tokenProvider = new GoogleOauth(app, R.raw.service_account);


        Interceptor peekJson = chain -> {
            String bearer = null;
            try {
//                bearer = "Bearer " + tokenProvider.getToken();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return chain.proceed(
                    chain.request().newBuilder().addHeader("Authorization", bearer).build()
            );
//            okhttp3.Response r = chain.proceed(chain.request());
//            String ct = r.header("Content-Type", "");
//            if (ct != null && ct.contains("application/json")) {
//                okhttp3.ResponseBody peek = r.peekBody(512 * 1024);
//                Log.d("GoogleJSON", peek.string());
//            }
//            return r;
        };

        OkHttpClient ok = new OkHttpClient.Builder()
                .addInterceptor(peekJson)
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
