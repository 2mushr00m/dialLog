package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.List;
import com.example.diallog.data.model.TranscriptSegment;


public interface Transcriber {
    @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri);

    default @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri, @NonNull String languageCode) {
        return transcribe(audioUri);
    }
}