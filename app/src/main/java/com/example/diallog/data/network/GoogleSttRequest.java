package com.example.diallog.data.network;

public final class GoogleSttRequest {
    public Config config;
    public Audio audio;

    public static final class Config {
        public String encoding;
        public int sampleRateHertz;
        public String languageCode;
        public String[] alternativeLanguageCodes;
        public boolean enableAutomaticPunctuation;
    }

    public static final class Audio {
        public String content;
    }
}