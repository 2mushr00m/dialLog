package com.example.diallog.data.repository.cache;


import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.TranscriptSegment;

import java.util.List;

public interface TranscriptCache {
    List<TranscriptSegment> get(@NonNull Uri uri);
    void put(@NonNull Uri uri, @NonNull List<TranscriptSegment> segs);
    boolean has(@NonNull Uri uri);
    void clear();
}