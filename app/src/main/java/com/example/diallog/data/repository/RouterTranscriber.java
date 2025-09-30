package com.example.diallog.data.repository;

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.R;
import com.example.diallog.data.model.Transcript;
import com.example.diallog.data.model.TranscriberResult;
import com.example.diallog.utils.AudioSnipper;
import com.example.diallog.utils.LangMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class RouterTranscriber implements Transcriber {
    private static final String TAG = "STT Router";

    private static final String DEFAULT_LANGUAGE_CODE = "en-US";
    private static final int SNIP_SECONDS = 30;


    private final Transcriber clova;
    private final GoogleTranscriber google;
    private final LanguageDetector detector;
    public RouterTranscriber(@NonNull Transcriber clova,
                             @NonNull GoogleTranscriber google,
                             @NonNull LanguageDetector detector) {
        this.clova = clova;
        this.google = google;
        this.detector = detector;
    }

    @Override
    public @NonNull TranscriberResult transcribe(@NonNull Uri audioUri) {
        return transcribeWithRouting(audioUri);
    }


    public @NonNull TranscriberResult transcribeWithRouting(@NonNull Uri audioUri) {
        long routeStart = SystemClock.elapsedRealtime();

        AudioSnipper snipper = new AudioSnipper(null, R.raw.sample1, "sample1_snip.mp3");
        AudioSnipper.SnippedAudio snippedAudio = snipper.snipHead(audioUri, SNIP_SECONDS);
        long quickStart = SystemClock.elapsedRealtime();
        TranscriberResult quickResult = null;
        if (snippedAudio != null && !snippedAudio.isEmpty()) {
            try {
                quickResult = google.transcribeQuick(snippedAudio.data, snippedAudio.sampleRateHz, DEFAULT_LANGUAGE_CODE);
            } catch (RuntimeException quickError) {
                Log.e(TAG, "[STT 스니퍼 실패]", quickError);
            }
        } else {
            Log.i(TAG, "[STT 라우팅 스킵] 이유: 스니퍼=없음");
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

        TranscriberResult finalResult;
        List<Transcript> finalSegments = Collections.emptyList();

        String clovaLanguage = LangMap.toClovaCode(languageTag);
        if (clovaLanguage != null) {
            Log.i(TAG, "route.decision provider=clova lang=" + clovaLanguage);
            try {
                TranscriberResult clovaResult = clova.transcribe(audioUri);
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
        TranscriberResult googleResult = google.transcribe(audioUri, finalLanguage, GoogleTranscriber.Mode.FULL);
        finalSegments = copySegments(googleResult.segments);
        finalResult = buildFinal(finalSegments, provider, route, snippetLen, languageTag, finalLanguage, routeStart);
        return finalResult;
    }
    @NonNull
    private static List<Transcript> copySegments(List<Transcript> segments) {
        if (segments == null || segments.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(segments);
    }

    @NonNull
    private static String extractSnippet(@NonNull List<Transcript> segments) {
        for (Transcript segment : segments) {
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

    private TranscriberResult buildFinal(@NonNull List<Transcript> segments,
                                         @NonNull String provider,
                                         @NonNull String route,
                                         int snippetLen,
                                         @Nullable String detectedTag,
                                         @NonNull String finalLanguageCode,
                                         long routeStart) {
        TranscriberResult.Metadata metadata = new TranscriberResult.Metadata(
                provider,
                route,
                snippetLen,
                detectedTag,
                finalLanguageCode
        );
        Log.i(TAG, "final.done provider=" + provider + " totalMs=" + (SystemClock.elapsedRealtime() - routeStart));
        return new TranscriberResult(segments, metadata, true);
    }
}