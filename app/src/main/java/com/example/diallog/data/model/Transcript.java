package com.example.diallog.data.model;

public final class Transcript {
    public final String text;
    public final long startMs;
    public final long endMs;
    public final Double confidence;
    public final String speakerLabel;

    public Transcript(String text, long startMs, long endMs, Double confidence, String speakerLabel) {
        this.text = text;
        this.startMs = startMs;
        this.endMs = endMs;
        this.confidence = confidence;
        this.speakerLabel = speakerLabel;
    }
}
