package com.example.diallog.data.repository;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;


import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.nl.languageid.IdentifiedLanguage;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import java.util.List;

public final class LanguageDetectHelper implements LanguageDetector {
    private static final String TAG = "LangDetect";

    @Override public @NonNull String detect(@NonNull Uri audioUri) {
        return "ko-KR";
    }

    public static void detectLanguage(String text) {
        LanguageIdentifier identifier = LanguageIdentification.getClient();

        identifier.identifyLanguage(text)
                .addOnSuccessListener(new OnSuccessListener<String>() {
                    @Override
                    public void onSuccess(String languageCode) {
                        if (languageCode.equals("und")) {
                            Log.i(TAG, "언어를 인식할 수 없음");
                        } else {
                            Log.i(TAG, "인식된 언어 코드: " + languageCode);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "언어 감지 실패", e);
                    }
                });
    }


    public static void detectPossibleLanguages(String text) {
        LanguageIdentifier identifier = LanguageIdentification.getClient();

        identifier.identifyPossibleLanguages(text)
                .addOnSuccessListener(new OnSuccessListener<List<IdentifiedLanguage>>() {
                    @Override
                    public void onSuccess(List<IdentifiedLanguage> languages) {
                        for (IdentifiedLanguage lang : languages) {
                            String code = lang.getLanguageTag();
                            float confidence = lang.getConfidence();
                            Log.i(TAG, "후보 언어: " + code + " (신뢰도=" + confidence + ")");
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "언어 후보 감지 실패", e);
                    }
                });
    }
}