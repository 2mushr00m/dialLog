package com.example.diallog.data.network;

public final class GoogleSttRequest {
    public Config config;
    public Audio audio;

    public static final class Config {
        public String languageCode;
    }

    public static final class Audio {
        public String content;
    }
}