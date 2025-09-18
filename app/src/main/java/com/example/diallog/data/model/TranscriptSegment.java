package com.example.diallog.data.model;

public final class TranscriptSegment {
    public final String text;
    public final long startMs;
    public final long endMs;

    public TranscriptSegment(String text, long startMs, long endMs) {
        this.text = text;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    public TranscriptSegment(long startMs, long endMs, String text) {
        this(text, startMs, endMs);
    }
}
