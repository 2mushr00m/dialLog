package com.example.diallog.data.network;

public final class GoogleOperationResponse {
    public String name;
    public boolean done;
    public GoogleSttResponse response;
    public Status error;

    public static final class Status {
        public int code;
        public String message;
    }
}