package com.example.diallog.data.repository.cache;


import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.Transcript;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class FileTranscriptCache implements TranscriptCache {
    private static final String TAG = "STTCache";
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evicts = new AtomicLong();
    private final AtomicLong puts = new AtomicLong();

    private final File dir;
    private final Context app;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<List<Transcript>>(){}.getType();
    private final int maxEntries;

    public FileTranscriptCache(Context ctx, int maxEntries){
        this.app = ctx.getApplicationContext();
        this.dir = new File(ctx.getFilesDir(), "transcripts");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        this.maxEntries = Math.max(16, maxEntries);
    }

    @Override public List<Transcript> get(@NonNull Uri uri) {
        File f = fileFor(uri);
        if (!f.exists()) {
            misses.incrementAndGet();
            Log.i(TAG,"miss "+f.getName());
            return Collections.emptyList();
        }
        hits.incrementAndGet();
        Log.i(TAG,"hit "+f.getName());
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            List<Transcript> list = gson.fromJson(br, listType);
            // LRU 갱신: 마지막 접근 시각을 mtime으로 표시
            //noinspection ResultOfMethodCallIgnored
            f.setLastModified(System.currentTimeMillis());
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) { return Collections.emptyList(); }
    }

    @Override public void put(@NonNull Uri uri, @NonNull List<Transcript> segs) {
        File f = fileFor(uri);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            bw.write(gson.toJson(segs, listType));
        } catch (Exception ignore) {}
        puts.incrementAndGet();
        Log.i(TAG,"put "+fileFor(uri).getName()+" size="+fileFor(uri).length());
        evictIfNeeded();
    }

    @Override public boolean has(@NonNull Uri uri) {
        return fileFor(uri).exists();
    }

    @Override public void clear() {
        File[] all = dir.listFiles(); if (all == null) return;
        for (File f: all)
            f.delete();
    }

    private File fileFor(Uri uri) {
        return new File(dir, CacheKey.stableKey(app, uri) + ".json");
    }
    private void evictIfNeeded() {
        File[] files = dir.listFiles();
        if (files == null || files.length <= maxEntries) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int over = files.length - maxEntries;
        for (int i=0; i<over; i++) { try { files[i].delete(); } catch (Exception ignore) {} }
    }
}