package com.example.diallog.data.repository.cache;


import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.data.model.Transcript;

import java.util.List;

public interface TranscriptCache {
    List<Transcript> get(@NonNull Uri uri);
    String getSummary(@NonNull Uri uri);
    void put(@NonNull Uri uri,
             @NonNull List<Transcript> segs,
             @Nullable String summary,
             @Nullable String allText);

    boolean has(@NonNull Uri uri);

    void clear(@NonNull Uri uri);
    void clearAll();

    final class Meta {
        public final long durationMs;
        @Nullable public final String allText;
        @Nullable public final String summary;
        public Meta(long durationMs, @Nullable String allText, @Nullable String summary) {
            this.durationMs = durationMs;   this.allText = allText;     this.summary = summary;
        }
    }
    @Nullable Meta peekMeta(@NonNull Uri uri);

}