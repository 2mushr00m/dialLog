package com.example.diallog.data.repository;

import android.net.Uri;

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


    @Override public @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri) {
        String lang = detector.detect(audioUri);
        return "ko-KR".equalsIgnoreCase(lang) ? clova.transcribe(audioUri) : google.transcribe(audioUri);
    }
}
