package com.example.diallog.utils;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class LangMap {
    private static final String DEFAULT_GOOGLE = "en-US";

    @NonNull
    public static String toGoogleCode(@Nullable String languageTag) {
        if (TextUtils.isEmpty(languageTag)) {
            return DEFAULT_GOOGLE;
        }
        Locale locale = Locale.forLanguageTag(languageTag);
        String language = locale.getLanguage();
        if (TextUtils.isEmpty(language)) {
            return DEFAULT_GOOGLE;
        }
        if ("ko".equalsIgnoreCase(language)) {
            return "ko-KR";
        }
        if ("en".equalsIgnoreCase(language)) {
            String country = locale.getCountry();
            if (!TextUtils.isEmpty(country)) {
                return "en-" + country.toUpperCase(Locale.US);
            }
            return DEFAULT_GOOGLE;
        }
        String country = locale.getCountry();
        if (!TextUtils.isEmpty(country)) {
            return language.toLowerCase(Locale.US) + "-" + country.toUpperCase(Locale.US);
        }
        return language.toLowerCase(Locale.US);
    }

    @Nullable
    public static String toClovaCode(@Nullable String languageTag) {
        if (TextUtils.isEmpty(languageTag)) {
            return null;
        }
        Locale locale = Locale.forLanguageTag(languageTag);
        String language = locale.getLanguage();
        if ("ko".equalsIgnoreCase(language)) {
            return "ko-KR";
        }
        return null;
    }
}