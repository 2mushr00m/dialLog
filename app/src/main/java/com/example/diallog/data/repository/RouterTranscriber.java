package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.TranscriptSegment;

import java.util.List;
import android.util.Log;

public final class RouterTranscriber implements Transcriber {
    private static final String TAG = "RouterTranscriber";

    private final Transcriber clova;   // Naver Clova
    private final Transcriber google;    // Google STT
    private final LanguageDetector detector; // 간단 휴리스틱/모델

    private static final String KOREAN = "ko-KR";
    private static final String FALLBACK = "en-US";

    public RouterTranscriber(Transcriber clova, Transcriber google, LanguageDetector detector) {
        this.clova = clova;
        this.google = google;
        this.detector = detector;
    }

    @Override public @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri) {
        String lang = detector.detect(audioUri);
        Log.i(TAG, "transcribe: detected language=" + lang + " uri=" + audioUri);

        if (lang == null || lang.isEmpty()) {
            Log.w(TAG, "transcribe: empty language -> fallback=" + FALLBACK);
            lang = FALLBACK;
        }
        if (KOREAN.equalsIgnoreCase(lang)) {
            Log.i(TAG, "transcribe: using clova for language=" + lang);
            return clova.transcribe(audioUri);
        }
        Log.i(TAG, "transcribe: using google for language=" + lang);
        return google.transcribe(audioUri, lang);
    }
}
