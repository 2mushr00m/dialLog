package com.example.diallog.data.repository;

import android.media.MediaMetadataRetriever;

import com.example.diallog.data.model.CallRecord;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public final class FileSystemCallRepository implements CallRepository {
    private final File baseDir;
    private final List<CallRecord> cached;

    public FileSystemCallRepository(File baseDir) {
        this.baseDir = baseDir;
        this.cached = scanAllSorted();
    }

    @Override
    public List<CallRecord> getRecent(int offset, int limit) {
        int from = Math.max(0, offset);
        int to = Math.min(cached.size(), from + Math.max(0, limit));
        if (from >= to) return Collections.emptyList();
        return new ArrayList<>(cached.subList(from, to));
    }

    @Override
    public CallRecord getByPath(String path) {
        for (CallRecord r : cached) {
            if (r.path.equals(path)) return r;
        }
        return null;
    }


    private List<CallRecord> scanAllSorted() {
        List<CallRecord> out = new ArrayList<>();
        if (baseDir == null || !baseDir.exists()) return out;

        List<File> files = new ArrayList<>();
        collectAudioFilesRecursive(baseDir, files);

        for (File f : files) {
            long durationMs = extractDurationMs(f);
            long createdTime = f.lastModified();
            out.add(new CallRecord(
                    f.getAbsolutePath(),
                    f.getName(),
                    durationMs,
                    createdTime
            ));
        }

        // 최신순 정렬
        out.sort(Comparator.comparingLong((CallRecord r) -> r.createdTime).reversed());
        return out;
    }

    /** 하위 폴더 포함 오디오 파일 수집 */
    private void collectAudioFilesRecursive(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        Arrays.sort(list); // 안정적 순회
        for (File f : list) {
            if (f.isDirectory()) {
                collectAudioFilesRecursive(f, out);
            } else if (isAudio(f)) {
                out.add(f);
            }
        }
    }

    /** 확장자 기준 간단 판정 */
    private boolean isAudio(File f) {
        String name = f.getName().toLowerCase();
        return name.endsWith(".m4a") || name.endsWith(".mp3")
                || name.endsWith(".wav") || name.endsWith(".amr")
                || name.endsWith(".aac") || name.endsWith(".ogg");
    }

    /** MediaMetadataRetriever로 길이 추출 실패 시 0 */
    private long extractDurationMs(File f) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(f.getAbsolutePath());
            String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (dur == null) return 0L;
            return Long.parseLong(dur);
        } catch (Throwable t) {
            return 0L;
        } finally {
            mmr.release();
        }
    }
}
