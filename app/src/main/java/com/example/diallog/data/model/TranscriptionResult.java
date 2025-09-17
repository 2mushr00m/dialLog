package com.example.diallog.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TranscriptionResult {
    public final List<TranscriptSegment> segments;
    public final boolean isFinal;
    @Nullable public final Metadata metadata;

    public TranscriptionResult(@NonNull List<TranscriptSegment> segments,
                               boolean isFinal,
                               @Nullable Metadata metadata) {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        this.isFinal = isFinal;
        this.metadata = metadata;
    }

    @NonNull
    public static TranscriptionResult finalResult(@NonNull List<TranscriptSegment> segments,
                                                  @Nullable Metadata metadata) {
        return new TranscriptionResult(segments, true, metadata);
    }

    @NonNull
    public static TranscriptionResult interim(@NonNull List<TranscriptSegment> segments,
                                              @Nullable Metadata metadata) {
        return new TranscriptionResult(segments, false, metadata);
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