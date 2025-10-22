package com.example.diallog.data.repository.cache;


import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.data.model.Transcript;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class FileTranscriptCache implements TranscriptCache {
    private static final String TAG = "STTCache";

    private final Context app;
    private final File dir;
    private final int maxEntries;


    public FileTranscriptCache(Context ctx, int maxEntries){
        this.app = ctx.getApplicationContext();
        this.dir = new File(app.getCacheDir(), "transcripts");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "캐시 디렉터리 생성 실패: " + dir.getAbsolutePath());
        }
        this.maxEntries = Math.max(1, maxEntries);
    }

    private File fileFor(Uri uri) {
        return new File(dir, CacheKey.stableKey(app, uri) + ".json");
    }

    @Override public boolean has(@NonNull Uri uri) {
        return fileFor(uri).exists();
    }
    @Override public void clear(@NonNull Uri uri) {
        File f = fileFor(uri);
        if (f.exists() && !f.delete()) Log.w(TAG, "삭제 실패: " + f.getName());
    }
    @Override public void clearAll() {
        File[] all = dir.listFiles(); if (all == null) return;
        for (File f : all) try { if (!f.delete()) Log.w(TAG, "삭제 실패: " + f.getName()); } catch (Exception ignore) {}
    }


    @Override public @NonNull List<Transcript> get(@NonNull Uri uri) {
        File f = fileFor(uri);
        if (!f.exists()) {
            Log.i(TAG, "캐시 미스: file=" + f.getName());
            return Collections.emptyList();
        }
        Log.i(TAG, "캐시 히트: file=" + f.getName());
        try (InputStream in = new FileInputStream(f);
             Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            String json = readAll(r);
            // noinspection ResultOfMethodCallIgnored
            f.setLastModified(System.currentTimeMillis());
            JSONObject root = new JSONObject(json);
            return CacheJson.parseSegments(root);
        } catch (Exception e) {
            Log.w(TAG, "get 실패: " + f.getName() + " cause=" + e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    @Override public void put(@NonNull Uri uri,
                    @NonNull List<Transcript> segs,
                    @Nullable String summary,
                    @Nullable String allText) {
        File f = fileFor(uri);
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp, false);
             Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(w)) {
            JSONObject json = CacheJson.toJson(segs, summary, allText);
            bw.write(json.toString());
            bw.flush();
        } catch (Exception e) {
            Log.w(TAG, "임시 파일 기록 실패: " + tmp.getName() + " cause=" + e.getMessage(), e);
            // noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }

        if (!tmp.renameTo(f)) {
            try (InputStream in = new FileInputStream(tmp);
                 OutputStream out = new FileOutputStream(f, false)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            } catch (Exception e) {
                Log.w(TAG, "캐시 저장 실패: " + f.getName() + " cause=" + e.getMessage(), e);
            } finally { // noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
        Log.i(TAG, "캐시 저장: " + f.getName() + " size=" + f.length());
        evictIfNeeded();
    }
    @Override public @Nullable Meta peekMeta(@NonNull Uri uri) {
        File f = fileFor(uri);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f);
             Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JSONObject root = new JSONObject(readAll(r));
            CacheJson.Meta m = CacheJson.parseMeta(root);
            return new Meta(m.durationMs, m.allText, m.summary);
        } catch (Exception e) {
            Log.w(TAG, "peekMeta 실패: uri=" + uri + " 원인=" + e.getMessage(), e);
            return null;
        }
    }
    @Override public @Nullable String getSummary(@NonNull Uri uri) {
        Meta m = peekMeta(uri);
        return m != null ? m.summary : null;
    }

    private static String readAll(Reader r) throws IOException {
        char[] buf = new char[8192];
        StringBuilder sb = new StringBuilder(8192);
        int n; while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
        return sb.toString();
    }
    private void evictIfNeeded() {
        File[] files = dir.listFiles();
        if (files == null || files.length <= maxEntries) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int over = files.length - maxEntries;
        for (int i = 0; i < over; i++) try { files[i].delete(); } catch (Exception ignore) {}
    }
}