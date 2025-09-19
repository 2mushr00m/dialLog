package com.example.diallog.data.model;

public final class TranscriptSegment {
    public final String text;
    public final long startMs;
    public final long endMs;
    public final Double confidence;
//    public final Object diarization;

    public TranscriptSegment(String text, long startMs, long endMs, Double confidence) {
        this.text = text;
        this.startMs = startMs;
        this.endMs = endMs;
        this.confidence = confidence;
    }
}
