package com.example.diallog.auth;

public interface AuthTokenProvider {
    String getToken() throws Exception;
    void invalidate();
}