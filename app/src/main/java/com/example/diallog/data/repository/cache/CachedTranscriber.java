package com.example.diallog.data.repository.cache;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.Transcript;
import com.example.diallog.data.model.TranscriberResult;
import com.example.diallog.data.repository.Transcriber;

import java.util.ArrayList;
import java.util.List;

public final class CachedTranscriber implements Transcriber {
    private final Transcriber delegate;
    private final TranscriptCache cache;

    public CachedTranscriber(@NonNull Transcriber delegate, @NonNull TranscriptCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public @NonNull TranscriberResult transcribe(@NonNull Uri audioUri) {
        List<Transcript> hit = cache.get(audioUri);
        if (!hit.isEmpty()) {
            return TranscriberResult.success(hit, null);
        }
        TranscriberResult fresh = delegate.transcribe(audioUri);
        if (fresh != null && fresh.isFinal && fresh.segments != null && !fresh.segments.isEmpty()) {
            cache.put(audioUri, new ArrayList<>(fresh.segments));
        }
        return fresh;
    }
}