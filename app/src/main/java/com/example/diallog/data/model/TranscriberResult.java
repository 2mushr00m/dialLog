package com.example.diallog.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

public final class TranscriberResult {
    @Nullable public final Metadata metadata;
    public final List<Transcript> segments;
    public final boolean isFinal;

    public TranscriberResult(@NonNull List<Transcript> segments,
                             @Nullable Metadata metadata,
                             boolean isFinal) {
        this.segments = List.copyOf(segments);
        this.metadata = metadata;
        this.isFinal = isFinal;
    }

    @NonNull
    public static TranscriberResult success(@NonNull List<Transcript> segments,
                                            @Nullable Metadata metadata) {
        return new TranscriberResult(segments, metadata, true);
    }

    @NonNull
    public static TranscriberResult failure(@NonNull List<Transcript> segments,
                                            @Nullable Metadata metadata) {
        return new TranscriberResult(segments, metadata, false);
    }

    public static final class Metadata {
        public final String provider;
        public final String route;
        public final int snippetLength;
        @Nullable public final String detectedLanguageTag;
        public final String finalLanguageCode;

        public Metadata(@NonNull String provider,
                        @NonNull String route,
                        int snippetLength,
                        @Nullable String detectedLanguageTag,
                        @NonNull String finalLanguageCode) {
            this.provider = Objects.requireNonNull(provider, "provider");
            this.route = Objects.requireNonNull(route, "route");
            this.snippetLength = Math.max(snippetLength, 0);
            this.detectedLanguageTag = detectedLanguageTag;
            this.finalLanguageCode = Objects.requireNonNull(finalLanguageCode, "finalLanguageCode");
        }
    }
}