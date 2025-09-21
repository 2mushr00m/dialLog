package com.example.diallog.data.model;

public final class TranscriptSegment {
    public final String text;
    public final long startMs;
    public final long endMs;
    public final Float confidence;
    public final String speakerLabel;

    public TranscriptSegment(String text, long startMs, long endMs, Float confidence, String speakerLabel) {
        this.text = text;
        this.startMs = startMs;
        this.endMs = endMs;
        this.confidence = confidence;
        this.speakerLabel = speakerLabel;
    }
}
