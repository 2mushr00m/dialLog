package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.*;
import com.example.diallog.data.model.TranscriptSegment;


public interface Transcriber {
    @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri);
}
