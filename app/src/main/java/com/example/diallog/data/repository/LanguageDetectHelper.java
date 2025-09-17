package com.example.diallog.data.repository;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.languageid.IdentifiedLanguage;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LanguageDetectHelper implements LanguageDetector {
    private static final String TAG = "LangDetect";
    private static final float MIN_CONFIDENCE = 0.5f;
    private static final float CONFIDENCE_GAP = 0.1f;
    private static final float HANGUL_RATIO_THRESHOLD = 0.35f;
    private static final int SHORT_TEXT_THRESHOLD = 4;

    private final LanguageIdentifier languageIdentifier;

    public LanguageDetectHelper() {
        this(LanguageIdentification.getClient());
    }

    LanguageDetectHelper(@NonNull LanguageIdentifier languageIdentifier) {
        this.languageIdentifier = languageIdentifier;
    }

    @Override
    public @NonNull DetectResult detectFromText(@Nullable String text) {
        String normalized = text == null ? "" : text.trim();
        int snippetLen = normalized.length();
        Log.i(TAG, "detect.start snippetLen=" + snippetLen);

        if (normalized.isEmpty()) {
            Log.i(TAG, "detect.result=UNKNOWN snippetLen=0 reason=empty");
            return DetectResult.UNKNOWN;
        }

        float hangulRatio = computeHangulRatio(normalized);
        DetectResult result = null;

        if (hangulRatio >= HANGUL_RATIO_THRESHOLD || (hangulRatio > 0f && snippetLen <= SHORT_TEXT_THRESHOLD)) {
            result = DetectResult.KO;
        } else {
            DetectResult mlResult = detectViaMlKit(normalized);
            if (mlResult != null) {
                result = mlResult;
            }
        }
        if (result == null) {
            result = hangulRatio > 0f ? DetectResult.KO : DetectResult.NON_KO;
        }

        Log.i(
                TAG,
                "detect.result=" + result
                        + " snippetLen=" + snippetLen
                        + " hangulRatio=" + String.format(Locale.US, "%.2f", hangulRatio)
        );
        return result;
    }
    @Nullable
    private DetectResult detectViaMlKit(@NonNull String text) {
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
                float confidence = lang.getConfidence();
                if (best == null || confidence > best.getConfidence()) {
                    if (best != null) {
                        second = Math.max(second, best.getConfidence());
                    }
                    best = lang;
                } else if (confidence > second) {
                    second = confidence;
                }
            }

            if (best == null) {
                return null;
            }

            float bestConfidence = best.getConfidence();
            if (bestConfidence < MIN_CONFIDENCE || (second > 0f && bestConfidence - second < CONFIDENCE_GAP)) {
                return DetectResult.UNKNOWN;
            }

            String bestTag = best.getLanguageTag();
            if (TextUtils.isEmpty(bestTag) || "und".equalsIgnoreCase(bestTag)) {
                return DetectResult.UNKNOWN;
            }


            return isKoreanTag(bestTag) ? DetectResult.KO : DetectResult.NON_KO;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.w(TAG, "detectViaMlKit: failed", e);
            return null;
        }
    }

    private static boolean isKoreanTag(@NonNull String tag) {
        String lower = tag.toLowerCase(Locale.US);
        return lower.startsWith("ko");
    }

    private static float computeHangulRatio(@NonNull String text) {
        int total = 0;
        int hangul = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (isHangul(ch)) {
                hangul++;
                total++;
            } else if (Character.isLetterOrDigit(ch)) {
                total++;
            }
        }
        if (total == 0) {
            return 0f;
        }
        return (float) hangul / (float) total;
    }

    private static boolean isHangul(char ch) {
        return (ch >= 0xAC00 && ch <= 0xD7A3)
                || (ch >= 0x1100 && ch <= 0x11FF)
                || (ch >= 0x3130 && ch <= 0x318F)
                || (ch >= 0xA960 && ch <= 0xA97F)
                || (ch >= 0xD7B0 && ch <= 0xD7FF);
    }
}