package com.example.diallog.data.network;

import androidx.annotation.NonNull;

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
    private static final String BASE_URL = "https://api.example.com/"; // TODO: 공급자 URL
    private static volatile Retrofit RETROFIT;

    public static Retrofit get(@NonNull String apiKey) {
        if (RETROFIT != null) return RETROFIT;
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BODY);

        Interceptor auth = chain -> {
            Request req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer " + apiKey) // TODO: 공급자 스펙에 맞게 변경
                    .build();
            return chain.proceed(req);
        };

        OkHttpClient ok = new OkHttpClient.Builder()
                .addInterceptor(auth)
                .addInterceptor(log)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();

        Gson gson = new GsonBuilder().create();

        RETROFIT = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        return RETROFIT;
    }


    public static Retrofit clova() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient ok = new OkHttpClient.Builder()
                .addInterceptor(log)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BuildConfig.STT_BASE) // 게이트웨이만 필요
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }


    private ApiClient() {}

}
