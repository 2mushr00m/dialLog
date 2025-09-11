package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.TranscriptSegment;

import java.util.ArrayList;
import java.util.List;

public final class GoogleTranscriber implements Transcriber {
    @Override public @NonNull List<TranscriptSegment> transcribe(@NonNull Uri uri) {
        List<TranscriptSegment> out = new ArrayList<>();
        out.add(new TranscriptSegment("[GoogleTranscriber] 임시", 0, 0));
        return out;
    }
}
