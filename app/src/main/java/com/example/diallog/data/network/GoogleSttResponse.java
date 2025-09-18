package com.example.diallog.data.network;

import java.util.List;

public final class GoogleSttResponse {
    public List<Result> results;

    public static final class Result {
        public List<Alternative> alternatives;
        public String resultEndTime;
    }

    public static final class Alternative {
        public String transcript;
        public List<WordInfo> words;
    }
    public static final class WordInfo {
        public String word;
        public String startTime;
        public String endTime;
    }
}