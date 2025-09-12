package com.example.diallog.data.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class GoogleSttResponse {
    public List<Result> results;

    public static final class Result {
        public List<Alternative> alternatives;
    }

    public static final class Alternative {
        public String transcript;
    }
}