package com.example.diallog.data.repository;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.TranscriptSegment;

import java.util.ArrayList;
import java.util.List;

public final class GoogleTranscriber implements Transcriber {
    @Override public List<TranscriptSegment> transcribe(@NonNull String path) {
        // 임시: 빈 결과 또는 간단 더미
        List<TranscriptSegment> out = new ArrayList<>();
        out.add(new TranscriptSegment("[GoogleTranscriber] 임시", 0, 0));
        return out;
    }
}
