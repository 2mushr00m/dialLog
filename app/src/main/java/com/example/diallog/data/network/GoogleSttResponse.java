package com.example.diallog.data.network;

import java.util.List;

public final class GoogleSttResponse {
    public List<Result> results;

    public static final class Result {
        public List<Alternative> alternatives;
    }

    public static final class Alternative {
        public String transcript;
        public List<WordInfo> words;
    }
    public static final class WordInfo {
        public String word;
        public TimeOffset startTime;
        public TimeOffset endTime;
    }

    public static final class TimeOffset {
        public String seconds;
        public int nanos;
    }
}