package com.example.diallog.data.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ClovaSpeechResponse {
    public String text;
    public List<Seg> segments;
    public static final class Seg {
        public String text;
        @SerializedName(value = "startMs", alternate = {"start"})
        public long startMs;
        @SerializedName(value = "endMs", alternate = {"end"})
        public long endMs;
    }

}
