package com.example.diallog.data.repository;

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.model.TranscriptionResult;
import com.example.diallog.utils.AudioSnipper;
import com.example.diallog.utils.LangMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import android.util.Log;

public final class RouterTranscriber implements Transcriber {
    private static final String TAG = "RouterTranscriber";

    private static final String QUICK_LANGUAGE = "en-US";
    private static final int SNIP_SECONDS = 20;


    private final Transcriber clova;
    private final GoogleTranscriber google;
    private final LanguageDetector detector;
    private final AudioSnipper snipper;
    public RouterTranscriber(@NonNull Transcriber clova,
                             @NonNull GoogleTranscriber google,
                             @NonNull LanguageDetector detector,
                             @NonNull AudioSnipper snipper) {
        this.clova = clova;
        this.google = google;
        this.detector = detector;
        this.snipper = snipper;
    }

    @Override
    public @NonNull TranscriptionResult transcribe(@NonNull Uri audioUri) {
        return transcribeWithRouting(audioUri);
    }


    public @NonNull TranscriptionResult transcribeWithRouting(@NonNull Uri audioUri) {
        long routeStart = SystemClock.elapsedRealtime();
        Log.i(TAG, "route.start uri=" + audioUri);

        AudioSnipper.SnippedAudio snippedAudio = snipper.snipHead(audioUri, SNIP_SECONDS);
        long quickStart = SystemClock.elapsedRealtime();
        TranscriptionResult quickResult = null;
        if (snippedAudio != null && !snippedAudio.isEmpty()) {
            try {
                quickResult = google.transcribeQuick(snippedAudio.data, snippedAudio.sampleRateHz, QUICK_LANGUAGE);
            } catch (RuntimeException quickError) {
                Log.e(TAG, "quick.google.failed", quickError);
            }
        } else {
            Log.i(TAG, "quick.skip reason=no_snip");
        }
        long quickElapsed = SystemClock.elapsedRealtime() - quickStart;

        String snippet = quickResult != null ? extractSnippet(quickResult.segments) : "";
        int snippetLen = snippet.length();
        Log.i(TAG, "quick.done ms=" + quickElapsed + " snippetLen=" + snippetLen);

        Optional<String> detectedTag = Optional.empty();
        if (snippetLen > 0) {
            detectedTag = detector.detect(snippet);
        }
        String languageTag = detectedTag.orElse(null);
        Log.i(TAG, "mlkit.detect result=" + (languageTag != null ? languageTag : "unknown")
                + " snippetLen=" + snippetLen);

        String routePrefix = quickResult != null ? "quick" : "quick_fail";
        String provider = "google";
        String route = routePrefix + "->google";
        String finalLanguage = LangMap.toGoogleCode(languageTag);

        TranscriptionResult finalResult;
        List<TranscriptSegment> finalSegments = Collections.emptyList();

        String clovaLanguage = LangMap.toClovaCode(languageTag);
        if (clovaLanguage != null) {
            Log.i(TAG, "route.decision provider=clova lang=" + clovaLanguage);
            try {
                TranscriptionResult clovaResult = clova.transcribe(audioUri, clovaLanguage);
                finalSegments = copySegments(clovaResult.segments);
                provider = "clova";
                route = routePrefix + "->clova";
                finalResult = buildFinal(finalSegments, provider, route, snippetLen, languageTag, clovaLanguage, routeStart);
                return finalResult;
            } catch (RuntimeException clovaError) {
                Log.e(TAG, "route.clova.failed fallback=google", clovaError);
                provider = "google";
                route = routePrefix + "->clova_fail->google";
                finalLanguage = LangMap.toGoogleCode(languageTag);
            }
        }

        Log.i(TAG, "route.decision provider=google lang=" + finalLanguage);
        TranscriptionResult googleResult = google.transcribe(audioUri, finalLanguage, GoogleTranscriber.Mode.FULL);
        finalSegments = copySegments(googleResult.segments);
        finalResult = buildFinal(finalSegments, provider, route, snippetLen, languageTag, finalLanguage, routeStart);
        return finalResult;
    }
    @NonNull
    private static List<TranscriptSegment> copySegments(List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(segments);
    }

    @NonNull
    private static String extractSnippet(@NonNull List<TranscriptSegment> segments) {
        for (TranscriptSegment segment : segments) {
            if (segment == null) {
                continue;
            }
            if (TextUtils.isEmpty(segment.text)) {
                continue;
            }
            String trimmed = segment.text.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }

        return "";
    }

    private TranscriptionResult buildFinal(@NonNull List<TranscriptSegment> segments,
                                           @NonNull String provider,
                                           @NonNull String route,
                                           int snippetLen,
                                           @Nullable String detectedTag,
                                           @NonNull String finalLanguageCode,
                                           long routeStart) {
        TranscriptionResult.Metadata metadata = new TranscriptionResult.Metadata(
                provider,
                route,
                snippetLen,
                detectedTag,
                finalLanguageCode
        );
        Log.i(TAG, "final.done provider=" + provider + " totalMs=" + (SystemClock.elapsedRealtime() - routeStart));
        return new TranscriptionResult(segments, true, metadata);
    }
}