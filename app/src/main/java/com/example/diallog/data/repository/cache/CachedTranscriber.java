package com.example.diallog.data.repository.cache;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.repository.Transcriber;

import java.util.List;

public final class CachedTranscriber implements Transcriber {
    private final Transcriber delegate;
    private final TranscriptCache cache;

    public CachedTranscriber(@NonNull Transcriber delegate, @NonNull TranscriptCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override public @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri) {
        List<TranscriptSegment> hit = cache.get(audioUri);
        if (!hit.isEmpty()) return hit;
        List<TranscriptSegment> fresh = delegate.transcribe(audioUri);
        if (fresh != null && !fresh.isEmpty()) cache.put(audioUri, fresh);
        return fresh;
    }
}