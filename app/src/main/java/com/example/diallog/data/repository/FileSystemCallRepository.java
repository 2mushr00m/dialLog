package com.example.diallog.data.repository;

import android.media.MediaMetadataRetriever;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.data.model.CallRecord;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public final class FileSystemCallRepository implements CallRepository {
    private final File baseDir;
    private volatile List<CallRecord> cached = null;
    private static final Set<String> AUDIO_EXT = new HashSet<>(Arrays.asList(
            "mp3","m4a","aac","wav","amr","3gp","ogg","opus"
    ));

    public FileSystemCallRepository(File baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public synchronized List<CallRecord> getRecent(int offset, int limit) {
        ensureScanned();
        int start = Math.max(0, offset);
        int end = Math.min(start + Math.max(1, limit), cached.size());
        if (start >= cached.size()) return Collections.emptyList();
        return new ArrayList<>(cached.subList(start, end));
    }

    @Override
    public CallRecord getByPath(String path) {
        ensureScanned();
        for (CallRecord r : cached) if (r.path.equals(path)) return r;
        return null;
    }

    private void ensureScanned() {
        if (cached != null) return;
        List<CallRecord> out = new ArrayList<>();
        scanDirRecursive(baseDir, out);
        out.sort((a, b) -> Long.compare(b.createdTime, a.createdTime));
        cached = out;
    }

    private void scanDirRecursive(@Nullable File dir, @NonNull List<CallRecord> sink) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanDirRecursive(f, sink);
                continue;
            }
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot <= 0) continue;
            String ext = name.substring(dot + 1).toLowerCase(Locale.KOREA);
            if (!AUDIO_EXT.contains(ext)) continue;

            long durationMs = readDurationMs(f);
            long createdMs = readCreatedMs(f); // 메타 → 실패 시 lastModified
            sink.add(new CallRecord(f.getAbsolutePath(), name, durationMs, createdMs));
        }
    }

    private long readDurationMs(@NonNull File file) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());
            String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (dur == null) return 0L;
            return Long.parseLong(dur);
        } catch (Throwable t) {
            return 0L;
        } finally {
            try { mmr.release(); } catch (Throwable ignored) {}
        }
    }

    private long readCreatedMs(@NonNull File file) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());
            String date = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
            Long parsed = parseExifLike(date);
            return (parsed != null && parsed > 0) ? parsed : file.lastModified();
        } catch (Throwable t) {
            return file.lastModified();
        } finally {
            try { mmr.release(); } catch (Throwable ignored) {}
        }
    }

    @Nullable
    private Long parseExifLike(@Nullable String s) {
        if (s == null) return null;
        String[] patterns = {
                "yyyyMMdd'T'HHmmss.SSSZ", "yyyyMMdd'T'HHmmssZ",
                "yyyyMMdd HHmmss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat df = new SimpleDateFormat(p, Locale.US);
                if (Build.VERSION.SDK_INT >= 24) df.setLenient(true);
                return df.parse(s).getTime();
            } catch (ParseException ignored) {}
        }
        return null;
    }


}
