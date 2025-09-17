package com.example.diallog.data.repository;

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.TranscriptSegment;

import java.util.Collections;
import java.util.List;
import android.util.Log;

public final class RouterTranscriber implements Transcriber {
    private static final String TAG = "RouterTranscriber";

    private static final String QUICK_LANGUAGE = "en-US";
    private static final long QUICK_TIMEOUT_MS = 20_000L;

    private final Transcriber clova;   // Naver Clova
    private final Transcriber google;    // Google STT
    private final LanguageDetector detector; // 간단 휴리스틱/모델

    public RouterTranscriber(@NonNull Transcriber clova,
                             @NonNull GoogleTranscriber google,
                             @NonNull LanguageDetector detector) {
        this.clova = clova;
        this.google = google;
        this.detector = detector;
    }

    @Override
    public @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri) {
        return transcribeWithRouting(audioUri);
    }

    public @NonNull List<TranscriptSegment> transcribeWithRouting(@NonNull Uri audioUri) {
        long routeStart = SystemClock.elapsedRealtime();
        Log.i(TAG, "route.start audioUri=" + audioUri);

        List<TranscriptSegment> quickSegments = Collections.emptyList();
        RuntimeException quickError = null;
        long quickStart = SystemClock.elapsedRealtime();
        try {
            quickSegments = google.transcribe(audioUri, QUICK_LANGUAGE);
        } catch (RuntimeException e) {
            quickError = e;
        }
        long quickElapsed = SystemClock.elapsedRealtime() - quickStart;

        if (quickError != null) {
            Log.e(TAG, "quick.google.failed ms=" + quickElapsed, quickError);
            Log.i(TAG, "detect.result=UNKNOWN snippetLen=0");
            Log.i(TAG, "route.decision=google reason=quick_failed");
            Log.i(TAG, "route.meta provider=google route=quick->final snippetLen=0 detect=UNKNOWN");
            Log.i(TAG, "final.provider=google totalMs=" + (SystemClock.elapsedRealtime() - routeStart));
            throw quickError;
        }

        String snippet = extractSnippet(quickSegments);
        int snippetLen = snippet.length();
        Log.i(TAG, "quick.google.done len=" + snippetLen + ", ms=" + quickElapsed);

        LanguageDetector.DetectResult detectResult;
        if (snippetLen == 0) {
            detectResult = LanguageDetector.DetectResult.UNKNOWN;
            Log.i(TAG, "detect.result=UNKNOWN snippetLen=0 reason=emptySnippet");
        } else {
            detectResult = detector.detectFromText(snippet);
            Log.i(TAG, "detect.result=" + detectResult + " snippetLen=" + snippetLen);
        }

        List<TranscriptSegment> finalSegments = quickSegments;
        String provider = "google";

        if (detectResult == LanguageDetector.DetectResult.KO) {
            Log.i(TAG, "route.decision=clova");
            try {
                finalSegments = clova.transcribe(audioUri);
                provider = "clova";
            } catch (RuntimeException clovaError) {
                Log.e(TAG, "route.clova.failed fallback=google", clovaError);
                finalSegments = quickSegments;
                provider = "google";
            }
        } else {
            Log.i(TAG, "route.decision=google");
        }

        Log.i(TAG, "route.meta provider=" + provider + " route=quick->final snippetLen=" + snippetLen + " detect=" + detectResult);
        Log.i(TAG, "final.provider=" + provider + " totalMs=" + (SystemClock.elapsedRealtime() - routeStart));
        return finalSegments != null ? finalSegments : Collections.emptyList();
    }

    @NonNull
    private static String extractSnippet(@NonNull List<TranscriptSegment> segments) {
        for (TranscriptSegment segment : segments) {
            if (segment == null) {
                continue;
            }
            String text = segment.text;
            if (TextUtils.isEmpty(text)) {
                continue;
            }
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }

        return "";
    }
}