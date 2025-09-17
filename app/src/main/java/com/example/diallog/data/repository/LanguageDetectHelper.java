package com.example.diallog.data.repository;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import com.example.diallog.R;
import com.example.diallog.auth.AuthTokenProvider;
import com.example.diallog.data.network.GoogleSttApi;
import com.example.diallog.data.network.GoogleSttRequest;
import com.example.diallog.data.network.GoogleSttResponse;
import com.example.diallog.utils.MediaResolver;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.languageid.IdentifiedLanguage;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import java.io.*;
import java.util.*;

import retrofit2.Response;
import retrofit2.Retrofit;
public final class LanguageDetectHelper implements LanguageDetector {
    private static final String TAG = "LangDetect";
    private static final long DEFAULT_SNIPPET_MS = 15_000L;
    private static final long MIN_SNIPPET_MS = 10_000L;
    private static final long MAX_SNIPPET_MS = 20_000L;
    private static final int DEFAULT_BYTES_PER_SECOND = 16_000;
    private static final int MIN_SNIPPET_BYTES = 32 * 1024;
    private static final int FALLBACK_SAMPLE_RATE_HZ = 16_000;
    private static final float MIN_CONFIDENCE = 0.5f;
    private static final float CONFIDENCE_GAP = 0.1f;
    private static final String FALLBACK_LANGUAGE = "en-US";
    private static final String[] DETECTION_ALT_LANGUAGES = new String[]{"ko-KR", "ja-JP", "zh-CN"};

    private static final Map<String, String> LANGUAGE_MAP;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("en", "en-US");
        map.put("en-us", "en-US");
        map.put("en-gb", "en-GB");
        map.put("ko", "ko-KR");
        map.put("ko-kr", "ko-KR");
        map.put("ja", "ja-JP");
        map.put("ja-jp", "ja-JP");
        map.put("zh", "cmn-Hans-CN");
        map.put("zh-cn", "cmn-Hans-CN");
        map.put("zh-tw", "cmn-Hant-TW");
        map.put("fr", "fr-FR");
        map.put("de", "de-DE");
        map.put("es", "es-ES");
        map.put("vi", "vi-VN");
        map.put("th", "th-TH");
        map.put("id", "id-ID");
        map.put("hi", "hi-IN");
        LANGUAGE_MAP = Collections.unmodifiableMap(map);
    }

    private final Context app;
    private final GoogleSttApi googleSttApi;
    private final AuthTokenProvider tokenProvider;
    private final LanguageIdentifier languageIdentifier;

    public LanguageDetectHelper(@NonNull Context context,
                                @NonNull Retrofit retrofit,
                                @NonNull AuthTokenProvider tokenProvider) {
        this.app = context.getApplicationContext();
        this.googleSttApi = retrofit.create(GoogleSttApi.class);
        this.tokenProvider = tokenProvider;
        this.languageIdentifier = LanguageIdentification.getClient();
    }

    @Override public @NonNull String detect(@NonNull Uri audioUri) {
//        return "ko-KR";
        MediaResolver.ResolvedAudio resolved;
        MediaResolver resolver = new MediaResolver(app);
        try {
            resolved = resolver.resolveWithFallback(audioUri, app.getResources(), R.raw.sample1, "sample1.mp3");
        } catch (Exception e) {
            Log.e(TAG, "detect: failed to resolve audio", e);
            return FALLBACK_LANGUAGE;
        }

        try {
            byte[] snippet = readSnippetBytes(resolved.file);
            int sampleRateHz = GoogleSttAudioHelper.extractSampleRateHz(resolved.file);
            if (snippet.length == 0) {
                Log.w(TAG, "detect: no snippet available, fallback to default language");
                return FALLBACK_LANGUAGE;
            }

            String transcript = requestSnippetTranscript(snippet, resolved.mime, sampleRateHz);
            if (TextUtils.isEmpty(transcript)) {
                Log.w(TAG, "detect: empty transcript from snippet, fallback to default language");
                return FALLBACK_LANGUAGE;
            }

            String mapped = inferLanguage(transcript.trim());
            Log.i(TAG, "detect: mapped language=" + mapped);
            return mapped;
        } catch (Exception e) {
            Log.e(TAG, "detect: failed", e);
            return FALLBACK_LANGUAGE;
        } finally {
            if (resolved.tempCopy) {
                // Best effort cleanup
                //noinspection ResultOfMethodCallIgnored
                resolved.file.delete();
            }
        }
    }

    private byte[] readSnippetBytes(@NonNull File file) throws IOException {
        long durationMs = extractDurationMs(file);
        long desiredMs = DEFAULT_SNIPPET_MS;
        if (durationMs > 0) {
            desiredMs = Math.min(durationMs, MAX_SNIPPET_MS);
            if (desiredMs < MIN_SNIPPET_MS && durationMs >= MIN_SNIPPET_MS) {
                desiredMs = MIN_SNIPPET_MS;
            } else if (desiredMs <= 0) {
                desiredMs = Math.min(durationMs, MAX_SNIPPET_MS);
            }
        }

        long bytesToRead;
        if (durationMs > 0) {
            bytesToRead = (file.length() * desiredMs) / durationMs;
        } else {
            bytesToRead = (DEFAULT_BYTES_PER_SECOND * desiredMs) / 1000L;
        }
        if (bytesToRead <= 0) {
            bytesToRead = (DEFAULT_BYTES_PER_SECOND * MAX_SNIPPET_MS) / 1000L;
        }
        bytesToRead = Math.max(bytesToRead, MIN_SNIPPET_BYTES);
        bytesToRead = Math.min(bytesToRead, file.length());

        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            long remaining = bytesToRead;
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int read = in.read(buf, 0, toRead);
                if (read == -1) {
                    break;
                }
                bout.write(buf, 0, read);
                remaining -= read;
            }
            return bout.toByteArray();
        }
    }

    private long extractDurationMs(@NonNull File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (!TextUtils.isEmpty(durationStr)) {
                return Long.parseLong(durationStr);
            }
        } catch (Exception e) {
            Log.w(TAG, "extractDurationMs: failed to read metadata", e);
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return -1L;
    }

    private String requestSnippetTranscript(@NonNull byte[] snippet, @Nullable String mimeType, int sampleRateHz) throws Exception {
        GoogleSttRequest request = new GoogleSttRequest();
        request.config = new GoogleSttRequest.Config();
        request.config.languageCode = FALLBACK_LANGUAGE;
        request.config.alternativeLanguageCodes = DETECTION_ALT_LANGUAGES;
        GoogleSttAudioHelper.applyEncoding(request.config, mimeType, sampleRateHz);
        request.audio = new GoogleSttRequest.Audio();
        request.audio.content = Base64.encodeToString(snippet, Base64.NO_WRAP);

        Response<GoogleSttResponse> response = googleSttApi.recognize("Bearer " + tokenProvider.getToken(), request).execute();
        if (response.code() == 401 || response.code() == 403) {
            tokenProvider.invalidate();
            response = googleSttApi.recognize("Bearer " + tokenProvider.getToken(), request).execute();
        }

        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException("Google STT snippet failed: HTTP " + response.code());
        }

        GoogleSttResponse body = response.body();
        if (body == null || body.results == null || body.results.isEmpty()) {
            return "";
        }

        StringBuilder transcript = new StringBuilder();
        for (GoogleSttResponse.Result result : body.results) {
            if (result == null || result.alternatives == null || result.alternatives.isEmpty()) {
                continue;
            }
            for (GoogleSttResponse.Alternative alt : result.alternatives) {
                if (alt == null || TextUtils.isEmpty(alt.transcript)) {
                    continue;
                }
                String trimmed = alt.transcript.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (transcript.length() > 0) {
                    transcript.append(' ');
                }
                transcript.append(trimmed);
                break;
            }
        }
        return transcript.length() > 0 ? transcript.toString() : "";
    }

    private String inferLanguage(@NonNull String text) {
        try {
            List<IdentifiedLanguage> candidates = Tasks.await(languageIdentifier.identifyPossibleLanguages(text));
            if (candidates == null) {
                candidates = new ArrayList<>();
            }

            IdentifiedLanguage best = null;
            float second = 0f;
            for (IdentifiedLanguage lang : candidates) {
                if (lang == null) {
                    continue;
                }
                String tag = lang.getLanguageTag();
                if (TextUtils.isEmpty(tag) || "und".equalsIgnoreCase(tag)) {
                    continue;
                }
                float conf = lang.getConfidence();
                if (best == null || conf > best.getConfidence()) {
                    if (best != null) {
                        second = Math.max(second, best.getConfidence());
                    }
                    best = lang;
                } else if (conf > second) {
                    second = conf;
                }
            }

            if (best == null) {
                Log.w(TAG, "inferLanguage: unable to determine language from candidates");
                return FALLBACK_LANGUAGE;
            }

            float bestConf = best.getConfidence();
            if (bestConf < MIN_CONFIDENCE || (second > 0 && bestConf - second < CONFIDENCE_GAP)) {
                Log.w(TAG, "inferLanguage: low confidence distribution best=" + bestConf + " second=" + second);
                return FALLBACK_LANGUAGE;
            }

            String mapped = mapLanguageTag(best.getLanguageTag());
            if (mapped == null) {
                Log.w(TAG, "inferLanguage: no mapping for tag=" + best.getLanguageTag());
                return FALLBACK_LANGUAGE;
            }
            return mapped;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.e(TAG, "inferLanguage: failed to identify language", e);
            return FALLBACK_LANGUAGE;
        }
    }

    private String mapLanguageTag(String tag) {
        if (TextUtils.isEmpty(tag)) {
            return null;
        }
        String lower = tag.toLowerCase(Locale.US);
        String mapped = LANGUAGE_MAP.get(lower);
        if (mapped != null) {
            return mapped;
        }
        int idx = lower.indexOf('-');
        if (idx > 0) {
            String base = lower.substring(0, idx);
            mapped = LANGUAGE_MAP.get(base);
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }
}