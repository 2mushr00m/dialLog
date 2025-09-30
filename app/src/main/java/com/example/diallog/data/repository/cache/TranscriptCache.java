package com.example.diallog.data.repository.cache;


import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.Transcript;

import java.util.List;

public interface TranscriptCache {
    List<Transcript> get(@NonNull Uri uri);
    void put(@NonNull Uri uri, @NonNull List<Transcript> segs);
    boolean has(@NonNull Uri uri);
    void clear();
}