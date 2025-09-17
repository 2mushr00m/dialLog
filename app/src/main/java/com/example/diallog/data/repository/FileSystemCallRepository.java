package com.example.diallog.data.repository;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.example.diallog.data.model.CallRecord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


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
    private static final String TAG = "Repo";
    private static final int MAX_DEPTH_SAF = 4;        // SAF 순회 최대 깊이
    private static final int MAX_FILES_SAF = 200;      // SAF에서 수집할 최대 파일 수
    private static final long TIME_BUDGET_MS = 600L;   // 한 스캔 호출 당 시간 예산
    private static final int MEDIASTORE_LIMIT = 300;

    private final Context appContext;
    @Nullable private volatile Uri userDirUri;
    private final Set<String> audioExt;
    private final Set<String> hints;
    private final List<CallRecord> cache = new ArrayList<>();
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean scanned = false;
    private volatile boolean scanning = false;
    @Nullable private volatile Future<?> ongoingScan;

    public FileSystemCallRepository(@NonNull Context context,
                                    @Nullable Uri dirUri,
                                    @NonNull Set<String> hints,
                                    @NonNull Set<String> audioExt) {
        this.appContext = context.getApplicationContext();
        this.userDirUri = dirUri;
        this.hints = new HashSet<>();
        for (String h : hints) {
            if (h != null) {
                this.hints.add(h.toLowerCase());
            }
        }
        this.audioExt = new HashSet<>();
        for (String ext : audioExt) {
            if (ext != null) {
                this.audioExt.add(ext.replace(".", "").toLowerCase());
            }
        }
    }


    /** 스캔 (백그라운드에서 호출) */
    @Override
    public synchronized void ensureScanned() {
        if (scanned) {
            Log.i(TAG, "ensureScanned: skip (already)");
            return;
        }
        if (scanning) {
            Log.i(TAG, "ensureScanned: skip (scanning)");
            return;
        }
        scanning = true;
        try {
            internalScan();
        } finally {
            scanning = false;
        }
    }
    @NonNull
    @Override
    public synchronized Future<?> refreshAsync() {
        if (scanning) {
            Log.i(TAG, "refreshAsync: already running");
            return ongoingScan != null ? ongoingScan : CompletableFuture.completedFuture(null);
        }
        scanning = true;
        scanned = false;
        ongoingScan = scanExecutor.submit(() -> {
            try {
                internalScan();
            } catch (Exception e) {
                Log.e(TAG, "refreshAsync: scan failed", e);
            } finally {
                synchronized (FileSystemCallRepository.this) {
                    scanning = false;
                    ongoingScan = null;
                }
            }
        });
        return ongoingScan;
    }


    @Override
    public void reload() {
        Future<?> future = refreshAsync();
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.e(TAG, "reload: failed to wait for scan", e);
        }
    }

    @Override
    public synchronized void setUserDirUri(@Nullable Uri uri) {
        if (userDirUri == null ? uri == null : userDirUri.equals(uri)) {
            Log.i(TAG, "setUserDirUri: unchanged");
            return;
        }
        userDirUri = uri;
        scanned = false;
        refreshAsync();
    }

    private void internalScan() {
        final long startTicks = SystemClock.uptimeMillis();
        final List<CallRecord> collected = new ArrayList<>();
        final Uri localUri = userDirUri;

        int safCount = 0;
        if (localUri != null) {
            try {
                List<CallRecord> saf = scanUserUri(localUri, startTicks);
                safCount = saf.size();
                collected.addAll(saf);
            } catch (Exception e) {
                Log.e(TAG, "internalScan: SAF scan failed", e);
            }
        }

        List<CallRecord> media = Collections.emptyList();
        try {
            media = scanMediaStore(MEDIASTORE_LIMIT);
            collected.addAll(media);
        } catch (Exception e) {
            Log.e(TAG, "internalScan: MediaStore scan failed", e);
        }
        LinkedHashMap<Uri, CallRecord> dedup = new LinkedHashMap<>();
        for (CallRecord cr : collected) {
            dedup.put(cr.uri, cr);
        }
        List<CallRecord> merged = new ArrayList<>(dedup.values());
        merged.sort((a, b) -> Long.compare(b.startedAtEpochMs, a.startedAtEpochMs));

        synchronized (this) {
            cache.clear();
            cache.addAll(merged);
            scanned = true;
        }

        long elapsed = SystemClock.uptimeMillis() - startTicks;
        Log.i(TAG, "internalScan: saf=" + safCount +
                " media=" + media.size() +
                " total=" + merged.size() +
                " took=" + elapsed + "ms");
    }

    /** SAF 폴더: 폴더 지정 → 파일 불러오기 */
    private List<CallRecord> scanUserUri(@NonNull Uri uri, long startTicks) {
        final List<CallRecord> out = new ArrayList<>(Math.min(64, MAX_FILES_SAF));
        final DocumentFile root = DocumentFile.fromTreeUri(appContext, uri);

        if (root == null || !root.isDirectory()) {
            Log.w(TAG, "scanUserUri: invalid tree uri=" + uri);
            return out;
        }

        final ArrayDeque<DocNode> dq = new ArrayDeque<>();
        dq.add(new DocNode(root, 0));

        int collected = 0;
        while (!dq.isEmpty()) {
            if (SystemClock.uptimeMillis() - startTicks > TIME_BUDGET_MS) break;

            final DocNode node = dq.removeFirst();
            final DocumentFile dir = node.dir;
            if (node.depth > MAX_DEPTH_SAF) continue;

            final DocumentFile[] children = dir.listFiles();
            if (children == null) continue;

            for (DocumentFile f : children) {
                if (collected >= MAX_FILES_SAF) break;
                if (SystemClock.uptimeMillis() - startTicks > TIME_BUDGET_MS) break;

                final String name = f.getName();
                if (name == null) continue;

                if (f.isDirectory()) {
                    if (shouldSkipDir(name)) {
                        continue;
                    }
                    dq.addLast(new DocNode(f, node.depth + 1));
                    continue;
                }

                if (!isAudioFile(f, name)) continue;

                final long startedAt = Math.max(0L, f.lastModified());
                out.add(new CallRecord(
                        f.getUri(), name, 0L, startedAt
                ));
                collected++;
            }
            if (collected >= MAX_FILES_SAF) break;
        }
        Log.i(TAG, "scanUserUri: dir=" + uri + " collected=" + out.size());
        return out;
    }

    private boolean shouldSkipDir(@NonNull String name) {
        String lower = name.toLowerCase();
        return name.startsWith(".") || "android".equalsIgnoreCase(name)
                || lower.contains("cache") || lower.contains("backup");
    }

    private boolean isAudioFile(@NonNull DocumentFile file, @NonNull String name) {
        String mime = file.getType();
        if (mime != null && mime.startsWith("audio/")) return true;
        return isAudio(name);
    }

    private static final class DocNode {
        final DocumentFile dir;
        final int depth;
        DocNode(DocumentFile d, int depth) { this.dir = d; this.depth = depth; }
    }

    /** MediaStore 검색: 경로/파일명에 힌트 포함 + 확장자 필터 */
    private List<CallRecord> scanMediaStore(int limit) {
        List<CallRecord> focused = queryMediaStore(limit, true, true);
        Log.i(TAG, "scanMediaStore: focused.size=" + focused.size());
        List<CallRecord> general = queryMediaStore(limit, false, true);
        Log.i(TAG, "scanMediaStore: general.size=" + general.size());
        List<CallRecord> broad = queryMediaStore(limit, false, false);
        Log.i(TAG, "scanMediaStore: broad.size=" + broad.size());

        LinkedHashMap<Uri, CallRecord> map = new LinkedHashMap<>();

        for (CallRecord cr : focused) map.put(cr.uri, cr);
        for (CallRecord cr : general) map.putIfAbsent(cr.uri, cr);
        for (CallRecord cr : broad) map.putIfAbsent(cr.uri, cr);

        List<CallRecord> merged = new ArrayList<>(map.values());
        merged.sort((a, b) -> Long.compare(b.startedAtEpochMs, a.startedAtEpochMs));

        Log.i(TAG, "scanMediaStore: merged.size=" + merged.size());
        return merged;
    }

    private List<CallRecord> queryMediaStore(int limit, boolean useHints, boolean useDialLogPath) {
        final List<CallRecord> out = new ArrayList<>(Math.min(128, limit));
        final ContentResolver cr = appContext.getContentResolver();
        final Uri base = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        final boolean api29Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        final String pathColumn = api29Plus
                ? MediaStore.Audio.Media.RELATIVE_PATH
                : MediaStore.Audio.Media.DATA;

        final String[] proj = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_MODIFIED,
                pathColumn
        };

        List<String> sel = new ArrayList<>();
        List<String> args = new ArrayList<>();

        sel.add(MediaStore.Audio.Media.MIME_TYPE + " LIKE ?");
        args.add("audio/%");
        sel.add(MediaStore.Audio.Media.SIZE + " > 0");
        sel.add(MediaStore.Audio.Media.DURATION + " >= ?");
        args.add("300");
        if (!audioExt.isEmpty()) {
            List<String> like = new ArrayList<>();
            for (String ext : audioExt) {
                like.add("LOWER(" + MediaStore.Audio.Media.DISPLAY_NAME + ") LIKE ?");
                args.add("%." + ext);
            }
            sel.add("(" + TextUtils.join(" OR ", like) + ")");
        }

        if (useDialLogPath) {
            String col = api29Plus
                    ? "LOWER(" + MediaStore.Audio.Media.RELATIVE_PATH + ")"
                    : "LOWER(" + MediaStore.Audio.Media.DATA + ")";
            sel.add("(" + col + " LIKE ? OR " + col + " LIKE ?)");
            args.add("%/music/diallog/%");
            args.add("%/diallog/%");
        }

        if (useHints && !hints.isEmpty()) {
            List<String> like = new ArrayList<>();
            for (String h : hints) {
                like.add("LOWER(" + MediaStore.Audio.Media.DISPLAY_NAME + ") LIKE ?");
                args.add("%" + h + "%");
                like.add("LOWER(" + pathColumn + ") LIKE ?");
                args.add("%" + h + "%");
            }
            sel.add("(" + TextUtils.join(" OR ", like) + ")");
        }
        String selection = TextUtils.join(" AND ", sel);
        String[] selectionArgs = args.toArray(new String[0]);

        Log.i(TAG, "queryMediaStore useHints=" + useHints +
                " useDialLogPath=" + useDialLogPath +
                " sel=" + selection +
                " args=" + Arrays.toString(selectionArgs));

        Cursor cursor;
        if (api29Plus) {
            Bundle qb = new Bundle();
            qb.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
            qb.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs);
            qb.putString(ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Audio.Media.DATE_ADDED);
            qb.putString(ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Audio.Media.DATE_MODIFIED);
            qb.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
            qb.putInt(ContentResolver.QUERY_ARG_LIMIT, Math.max(1, limit));
            cursor = cr.query(base, proj, qb, null);
        } else {
            String order = MediaStore.Audio.Media.DATE_MODIFIED + " DESC";
            cursor = cr.query(base, proj, selection, selectionArgs, order);
        }
        if (cursor == null) {
            Log.i(TAG, "queryMediaStore: null cursor");
            return out;
        }

        try {
            final int iId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            final int iName = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            final int iMime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);
            final int iSize = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            final int iDur  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            final int iDate = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);

            int added = 0;
            while (cursor.moveToNext()) {
                String name = cursor.getString(iName);
                if (name == null) continue;

                String mime = cursor.getString(iMime);
                long size = cursor.getLong(iSize);
                long durationMs = cursor.getLong(iDur);

                boolean mimeOk = mime != null && mime.startsWith("audio/");
                boolean nameOk = isAudio(name);
                if (!mimeOk && !nameOk) continue;
                if (size <= 0) continue;
                if (durationMs < 300) continue;

                long id = cursor.getLong(iId);
                Uri item = ContentUris.withAppendedId(base, id);
                long dateModifiedSec = cursor.getLong(iDate);
                long startedAt = Math.max(0L, dateModifiedSec * 1000L);

                out.add(new CallRecord(item, name, durationMs, startedAt));
                added++;
            }
            Log.i(TAG, "queryMediaStore: added=" + added);
        } finally {
            cursor.close();
        }
        return out;
    }

    private boolean isAudio(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = fileName.substring(dot + 1).toLowerCase();
        return audioExt.contains(ext);
    }

    @Override public synchronized List<CallRecord> getRecent(int offset, int limit) {
        Log.i(TAG, "getRecent offset=" + offset + " limit=" + limit + " scanned=" + scanned);
        int to = Math.min(cache.size(), offset + limit);
        Log.i(TAG, "getRecent cacheSize=" + cache.size() + " sublist=[" + offset + "," + to + ")");
        if (offset >= to) return Collections.emptyList();
        return new ArrayList<>(cache.subList(offset, to));
    }

    @Override public synchronized @Nullable CallRecord getByUri(@NonNull Uri uri) {
        for (CallRecord cr : cache) if (cr.uri.equals(uri)) return cr;
        return null;
    }
}
