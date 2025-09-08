package com.example.diallog.data.repository;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.TranscriptSegment;

import java.util.List;

public final class RouterTranscriber implements Transcriber {
    private final Transcriber clova;   // Naver Clova
    private final Transcriber google;    // Google STT
    private final LanguageDetector detector; // 간단 휴리스틱/모델

    public RouterTranscriber(Transcriber clova, Transcriber google, LanguageDetector detector) {
        this.clova = clova;
        this.google = google;
        this.detector = detector;
    }

    @Override public List<TranscriptSegment> transcribe(@NonNull String path) {
        String lang = detector.detect(path);
        return "ko-KR".equalsIgnoreCase(lang) ? clova.transcribe(path) : google.transcribe(path);
    }
}
