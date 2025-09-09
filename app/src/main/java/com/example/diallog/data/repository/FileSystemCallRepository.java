package com.example.diallog.data.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.data.model.CallRecord;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


/**
 * FileSystemCallRepository
 * - MediaStore에서 오디오를 최신순으로 스캔하고 통화녹음 후보만 필터합니다.
 * - getRecent(offset, limit): 오프셋/리미트 기반 페이징을 제공합니다.
 * - getByPath(path): 단일 파일 메타데이터를 반환합니다.
 *
 * 권한:
 *  - Android 13+(SDK 33+): android.permission.READ_MEDIA_AUDIO
 *  - Android 12L 이하: android.permission.READ_EXTERNAL_STORAGE
 */
public final class FileSystemCallRepository implements CallRepository {

    private static final String[] PROJECTION = new String[]{
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE
    };
    private final ContentResolver cr;
    private volatile List<CallRecord> cached = null;
    private static final Set<String> AUDIO_EXT = new HashSet<>(Arrays.asList(
            ".m4a", ".mp3", ".acc", ".wav", ".3gp","ogg","opus"));
    private static final Set<String> HINTS = new HashSet<>(Arrays.asList(
            "call", "record", "rec", "통화", "녹음", "전화"));

    public FileSystemCallRepository(@NonNull Context context) {
        this.cr = context.getApplicationContext().getContentResolver();
    }

    @Override @NonNull
    public List<CallRecord> getRecent(int offset, int limit) {
        List<CallRecord> page = new ArrayList<>(limit);
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String selection = MediaStore.Audio.Media.DURATION + " > ?";
        String[] selectionArgs = new String[]{ String.valueOf(0) };
        String sortOrder = MediaStore.Audio.Media.DATE_MODIFIED + " DESC";

        try (Cursor c = cr.query(uri, PROJECTION, selection, selectionArgs, sortOrder)) {
            if (c == null) return page;
            if (offset > 0 && !c.moveToPosition(offset)) return page;

            do {
                CallRecord rec = mapCandidate(c);
                if (rec != null) {
                    page.add(rec);
                    if (page.size() >= limit) break;
                }
            } while (c.moveToNext());
        }
        return page;
    }

    private CallRecord mapCandidate(@NonNull Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
        String data = getString(c, MediaStore.Audio.Media.DATA);
        String display = getString(c, MediaStore.Audio.Media.DISPLAY_NAME);
        String relPath = getString(c, MediaStore.Audio.Media.RELATIVE_PATH);
        long durationMs = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
        long dateModifiedSec = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED));
        long size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));

        // 1) 확장자 필터
        String extKey = !TextUtils.isEmpty(data) ? data : display;
        if (extKey == null) return null;
        String extKeyLower = extKey.toLowerCase(Locale.ROOT);
        if (!endsWithAny(extKeyLower, AUDIO_EXT)) return null;

        // 2) 파일명/경로 힌트 검사
        String pathTarget = "";
        if (!TextUtils.isEmpty(data)) {
            pathTarget = data;
        } else if (!TextUtils.isEmpty(relPath) || !TextUtils.isEmpty(display)) {
            String rp = relPath != null ? relPath : "";
            String dn = display != null ? display : "";
            pathTarget = rp + "/" + dn;
        }
        String nameTarget = display != null ? display : "";

        String pathLower = pathTarget.toLowerCase(Locale.ROOT);
        String nameLower = nameTarget.toLowerCase(Locale.ROOT);

        boolean hintHit = containsAny(pathLower, HINTS) || containsAny(nameLower, HINTS);

        // 3) 힌트 미적중 시 소음/벨소리 등 노이즈 제거
        if (!hintHit && durationMs < 5_000) return null;

        // 4) 대표 식별자 선택
        String pathOrUri = !TextUtils.isEmpty(data)
                ? data
                : Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id)).toString();

        // 5) startedAtEpochMs: DATE_MODIFIED(초) → ms
        long startedAtEpochMs = dateModifiedSec > 0 ? dateModifiedSec * 1000L : System.currentTimeMillis();

        // 6) 모델 매핑
        return new CallRecord(
                pathOrUri,
                display != null ? display : "audio_" + id,
                durationMs,
                startedAtEpochMs
        );
    }

    @Override @Nullable
    public CallRecord getByPath(@NonNull String path) {
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        // content Uri로 전달
        if (path.startsWith("content://")) {
            String sel = MediaStore.Audio.Media._ID + " = ?";
            // _ID를 직접 알 수 없는 경우가 많음 → content 문자열 비교로 후속 처리
            // 현실적으로는 이 분기에서 전수 스캔 → mapIfCandidate 경로/이름 힌트로 필터링이 안전
            // 여기서는 간단히 DISPLAY_NAME 일치 fallback로 처리
        }

        // 2) DATA(절대경로)
        String selData = MediaStore.Audio.Media.DATA + " = ?";
        try (Cursor c = cr.query(uri, PROJECTION, selData, new String[]{path}, null)) {
            if (c != null && c.moveToFirst()) {
                return mapCandidate(c);
            }
        }

        // 3) DISPLAY_NAME fallback
        String name = path.substring(path.lastIndexOf('/') + 1);
        String selName = MediaStore.Audio.Media.DISPLAY_NAME + " = ?";
        try (Cursor c2 = cr.query(uri, PROJECTION, selName, new String[]{name},
                MediaStore.Audio.Media.DATE_MODIFIED + " DESC")) {
            if (c2 != null && c2.moveToFirst()) {
                return mapCandidate(c2);
            }
        }
        return null;
    }

    @Nullable
    private static String getString(@NonNull Cursor c, @NonNull String col) {
        int idx = c.getColumnIndex(col);
        if (idx < 0) return null;
        return c.getString(idx);
    }

    private static boolean endsWithAny(@NonNull String textLower, @NonNull Set<String> suffixesLower) {
        for (String s : suffixesLower) {
            if (textLower.endsWith(s)) return true;
        }
        return false;
    }

    private static boolean containsAny(@NonNull String textLower, @NonNull Set<String> needlesLower) {
        if (textLower.isEmpty()) return false;
        for (String n : needlesLower) {
            if (textLower.contains(n)) return true;
        }
        return false;
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
