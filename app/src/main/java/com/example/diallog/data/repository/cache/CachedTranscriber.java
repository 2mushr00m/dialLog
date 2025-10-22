package com.example.diallog.data.repository.cache;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.Transcript;
import com.example.diallog.data.model.TranscriberResult;
import com.example.diallog.data.network.SummaryGenerator;
import com.example.diallog.data.repository.Transcriber;

import java.util.ArrayList;
import java.util.List;

public final class CachedTranscriber implements Transcriber {
    private final Transcriber delegate;
    private final TranscriptCache cache;
    private final SummaryGenerator summaryGenerator;

    public CachedTranscriber(@NonNull Transcriber delegate,
                             @NonNull TranscriptCache cache,
                             @NonNull SummaryGenerator summaryGenerator) {
        this.delegate = delegate;
        this.cache = cache;
        this.summaryGenerator = summaryGenerator;
    }

    @Override
    public @NonNull TranscriberResult transcribe(@NonNull Uri audioUri) {
        List<Transcript> hit = cache.get(audioUri);
        if (!hit.isEmpty()) {
            return TranscriberResult.success(hit, null);
        }

        TranscriberResult fresh = delegate.transcribe(audioUri);
        if (fresh != null && fresh.isFinal && fresh.segments != null && !fresh.segments.isEmpty()) {
            String allText = joinTexts(fresh.segments, 4000);

            String summary = null;
            try { summary = summaryGenerator.summarize(fresh.segments); }
            catch (Exception e) {
                summary = "요약 실패";
                Log.w("Summary","gen-fail: "+e.getMessage(), e); }

            cache.put(
                    audioUri,
                    new ArrayList<>(fresh.segments),
                    summary,
                    allText);
        }
        return fresh;
    }

    private static String joinTexts(List<Transcript> segs, int maxChars) {
        StringBuilder sb = new StringBuilder(Math.min(maxChars, 4096));
        for (Transcript t : segs) {
            if (t == null || t.text == null || t.text.isEmpty()) continue;
            if (sb.length() + t.text.length() + 1 > maxChars) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(t.text);
        }
        return sb.toString();
    }

}