package com.example.diallog.utils;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.data.repository.LanguageDetector;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.languageid.IdentifiedLanguage;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MlKitLanguageDetector implements LanguageDetector {
    private static final String TAG = "MlKitLangDetector";
    private static final float MIN_CONFIDENCE = 0.5f;

    private final LanguageIdentifier identifier;

    public MlKitLanguageDetector() {
        this(LanguageIdentification.getClient());
    }

    MlKitLanguageDetector(@NonNull LanguageIdentifier identifier) {
        this.identifier = identifier;
    }

    @Override
    public @NonNull Optional<String> detect(@Nullable String text) {

        return Optional.of("ko");

//        String normalized = text == null ? "" : text.trim();
//        int length = normalized.length();
//        Log.i(TAG, "mlkit.detect start snippetLen=" + length);
//        if (normalized.isEmpty()) {
//            Log.i(TAG, "mlkit.detect result=unknown reason=empty");
//            return Optional.empty();
//        }
//        try {
//            List<IdentifiedLanguage> candidates = Tasks.await(identifier.identifyPossibleLanguages(normalized));
//            if (candidates == null) {
//                candidates = new ArrayList<>();
//            }
//            IdentifiedLanguage best = null;
//            for (IdentifiedLanguage lang : candidates) {
//                if (lang == null) {
//                    continue;
//                }
//                String tag = lang.getLanguageTag();
//                if (TextUtils.isEmpty(tag) || "und".equalsIgnoreCase(tag)) {
//                    continue;
//                }
//                float confidence = lang.getConfidence();
//                if (confidence < MIN_CONFIDENCE) {
//                    continue;
//                }
//                if (best == null || confidence > best.getConfidence()) {
//                    best = lang;
//                }
//            }
//            if (best == null) {
//                Log.i(TAG, "mlkit.detect result=unknown reason=no_candidate");
//                return Optional.empty();
//            }
//            String tag = best.getLanguageTag();
//            Log.i(TAG, "mlkit.detect result=" + tag + " confidence=" + best.getConfidence());
//            return Optional.of(tag);
//        } catch (Exception e) {
//            if (e instanceof InterruptedException) {
//                Thread.currentThread().interrupt();
//            }
//            Log.w(TAG, "mlkit.detect failure", e);
//            return Optional.empty();
//        }
    }
}