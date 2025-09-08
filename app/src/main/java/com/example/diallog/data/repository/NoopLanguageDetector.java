package com.example.diallog.data.repository;

public final class NoopLanguageDetector implements LanguageDetector {
    @Override public String detect(String audioPath) { return "ko-KR"; }
}