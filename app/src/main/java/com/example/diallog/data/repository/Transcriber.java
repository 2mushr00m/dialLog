package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.List;
import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.model.TranscriptionResult;


public interface Transcriber {
    @NonNull
    TranscriptionResult transcribe(@NonNull Uri audioUri);

    default @NonNull TranscriptionResult transcribe(@NonNull Uri audioUri, @NonNull String languageCode) {
        return transcribe(audioUri);
    }
}