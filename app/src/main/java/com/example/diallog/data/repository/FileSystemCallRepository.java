package com.example.diallog.data.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.example.diallog.data.model.CallRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final String TAG = "Repo";
    private static final int MAX_DEPTH_SAF = 4;        // SAF 순회 최대 깊이
    private static final int MAX_FILES_SAF = 200;      // SAF에서 수집할 최대 파일 수
    private static final long TIME_BUDGET_MS = 250L;   // 한 스캔 호출 당 시간 예산

    private final Context appContext;
    @Nullable private final Uri dirUri;
    private final Set<String> AUDIO_EXT;
    private final Set<String> HINTS;
    private final List<CallRecord> cache = new ArrayList<>();
    private volatile boolean scanned = false;

    public FileSystemCallRepository(@NonNull Context context,
                                    @Nullable Uri dirUri,
                                    @NonNull Set<String> hints,
                                    @NonNull Set<String> audioExt) {
        this.appContext = context.getApplicationContext();
        this.dirUri = dirUri;
        this.HINTS = new HashSet<>(hints);
        for (String h : hints) this.HINTS.add(h.toLowerCase());
        this.AUDIO_EXT = new HashSet<>();
        for (String ext : audioExt) this.AUDIO_EXT.add(ext.replace(".", "").toLowerCase());
    }

    /** 스캔 (백그라운드에서 호출) */
    public synchronized void ensureScanned() {
        if (scanned) { Log.i(TAG, "ensureScanned: skip (already)"); return; }

        Log.i(TAG, "ensureScanned: start dirUri=" + (dirUri != null));
        final long t0 = android.os.SystemClock.uptimeMillis();

        final List<CallRecord> found = new ArrayList<>();
        try {
            if (dirUri != null) {
                Log.i(TAG, "ensureScanned: via SAF");
                found.addAll(scanUserUri(dirUri, t0));
            } else {
                Log.i(TAG, "ensureScanned: via MediaStore");
                found.addAll(scanMediaStore(300));
            }
        } catch (Exception e) {
            Log.e(TAG, "scan failed", e);
        }

        found.sort((a, b) -> Long.compare(b.startedAtEpochMs, a.startedAtEpochMs));
        cache.clear();
        cache.addAll(found);
        scanned = true;
        Log.i(TAG, "ensureScanned: done found=" + found.size());
    }

    /** SAF 폴더: 폴더 지정 → 파일 불러오기 */
    private List<CallRecord> scanUserUri(@NonNull Uri uri, long startTicks) {
        final List<CallRecord> out = new ArrayList<>(Math.min(64, MAX_FILES_SAF));
        final DocumentFile root = DocumentFile.fromTreeUri(appContext, uri);
        if (root == null || !root.isDirectory()) return out;

        final java.util.ArrayDeque<DocNode> dq = new java.util.ArrayDeque<>();
        dq.add(new DocNode(root, 0));

        int collected = 0;
        while (!dq.isEmpty()) {
            if (android.os.SystemClock.uptimeMillis() - startTicks > TIME_BUDGET_MS) break;

            final DocNode node = dq.removeFirst();
            final DocumentFile dir = node.dir;
            if (node.depth > MAX_DEPTH_SAF) continue;

            final DocumentFile[] children = dir.listFiles();
            if (children == null) continue;

            for (DocumentFile f : children) {
                if (collected >= MAX_FILES_SAF) break;
                if (android.os.SystemClock.uptimeMillis() - startTicks > TIME_BUDGET_MS) break;

                final String name = f.getName();
                if (name == null) continue;

                if (f.isDirectory()) {
                    if (name.startsWith(".") || name.equalsIgnoreCase("Android") ||
                            name.toLowerCase().contains("cache") || name.toLowerCase().contains("backup")) {
                        continue;
                    }
                    dq.addLast(new DocNode(f, node.depth + 1));
                    continue;
                }

                if (!isAudio(name)) continue;

                final long startedAt = Math.max(0L, f.lastModified());
                out.add(new CallRecord(
                        f.getUri(), name, 0L, startedAt
                ));
                collected++;
            }
            if (collected >= MAX_FILES_SAF) break;
        }
        return out;
    }
    private static final class DocNode {
        final androidx.documentfile.provider.DocumentFile dir;
        final int depth;
        DocNode(androidx.documentfile.provider.DocumentFile d, int depth) { this.dir = d; this.depth = depth; }
    }

    /** MediaStore 검색: 경로/파일명에 힌트 포함 + 확장자 필터 */
    private List<CallRecord> scanMediaStore(int limit) {
        List<CallRecord> hinted = queryMediaStore(limit, true);
        Log.i(TAG, "scanMediaStore: hinted.size=" + hinted.size());
        List<CallRecord> general = queryMediaStore(Math.max(limit * 2, 100), false);
        Log.i(TAG, "scanMediaStore: general.size=" + general.size());

        LinkedHashMap<Uri, CallRecord> map = new LinkedHashMap<>();
        for (CallRecord cr : hinted) map.put(cr.uri, cr);
        for (CallRecord cr : general) map.putIfAbsent(cr.uri, cr);

        List<CallRecord> merged = new ArrayList<>(map.values());
        merged.sort((a, b) -> {
            int sa = containsHint(a) ? 0 : 1;
            int sb = containsHint(b) ? 0 : 1;
            if (sa != sb) return Integer.compare(sa, sb);
            return Long.compare(b.startedAtEpochMs, a.startedAtEpochMs);
        });

        Log.i(TAG, "scanMediaStore: merged.size=" + merged.size());
        if (merged.size() > limit) {
            List<CallRecord> sub = new java.util.ArrayList<>(merged.subList(0, limit));
            Log.i(TAG, "scanMediaStore: return limited to " + sub.size());
            return sub;
        }
        return merged;
    }

    private List<CallRecord> queryMediaStore(int limit, boolean useHints) {
        final List<CallRecord> out = new ArrayList<>(Math.min(64, limit));
        final ContentResolver cr = appContext.getContentResolver();
        final Uri base = (Build.VERSION.SDK_INT >= 29)
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        final String[] proj = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED,
                (Build.VERSION.SDK_INT >= 29 ? MediaStore.Audio.Media.RELATIVE_PATH
                        : MediaStore.Audio.Media.DATA)
        };

        List<String> sel = new ArrayList<>();
        List<String> args = new ArrayList<>();

        // 기본 필터
        sel.add(MediaStore.Audio.Media.MIME_TYPE + " LIKE ?");
        args.add("audio/%");
        sel.add(MediaStore.Audio.Media.DURATION + " > ?");
        args.add("500"); // 0.5s 이상
        sel.add(MediaStore.Audio.Media.SIZE + " > 0");

        // 확장자 필터: LOWER(DISPLAY_NAME) LIKE %.ext
        if (!AUDIO_EXT.isEmpty()) {
            List<String> like = new ArrayList<>();
            for (String ext : AUDIO_EXT) {
                like.add("LOWER(" + MediaStore.Audio.Media.DISPLAY_NAME + ") LIKE ?");
                args.add("%." + ext);
            }
            sel.add("(" + TextUtils.join(" OR ", like) + ")");
        }

        // 경로 필터: LOWER(DISPLAY_NAME/PATH) LIKE %hint%
        if (useHints && !HINTS.isEmpty()) {
            List<String> like = new ArrayList<>();
            for (String h : HINTS) {
                String hint = h.toLowerCase();
                like.add("LOWER(" + MediaStore.Audio.Media.DISPLAY_NAME + ") LIKE ?");
                args.add("%" + hint + "%");
                if (Build.VERSION.SDK_INT >= 29) {
                    like.add("LOWER(" + MediaStore.Audio.Media.RELATIVE_PATH + ") LIKE ?");
                    args.add("%" + hint + "%");
                } else {
                    like.add("LOWER(" + MediaStore.Audio.Media.DATA + ") LIKE ?");
                    args.add("%" + hint + "%");
                }
            }
            sel.add("(" + TextUtils.join(" OR ", like) + ")");
        }


        String selection = TextUtils.join(" AND ", sel);
        String[] selectionArgs = args.toArray(new String[0]);

        Cursor cursor;
        if (Build.VERSION.SDK_INT >= 29) {
            Bundle qb = new Bundle();
            qb.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
            qb.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs);
            qb.putString(ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Audio.Media.DATE_ADDED);
            qb.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
            qb.putInt(ContentResolver.QUERY_ARG_LIMIT, Math.max(1, limit));
            cursor = cr.query(base, proj, qb, null);
        } else {
            String order = MediaStore.Audio.Media.DATE_ADDED + " DESC";
            cursor = cr.query(base, proj, selection, selectionArgs, order);
        }

        Log.i(TAG, "Query useHints=" + useHints +
                " sel=" + selection + " args=" + java.util.Arrays.toString(selectionArgs));

        try {
            if (cursor == null) {
                Log.i(TAG, "query returned null cursor");
                return out;
            }
            final int iId = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
            final int iName = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            final int iMime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);
            final int iSize = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            final int iDate = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED);
            final int iDur  = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            final int iRel = (Build.VERSION.SDK_INT >= 29) ?
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) :
                    cursor.getColumnIndex(MediaStore.Audio.Media.DATA);

            int nullName=0, notAudio=0, tooShort=0, zeroSize=0, added=0;

            while (cursor.moveToNext()) {
                String name = cursor.getString(iName);
                if (name == null) { nullName++; continue; }

                String mime = cursor.getString(iMime);
                long size = cursor.getLong(iSize);
                long dateAddedSec = cursor.getLong(iDate);
                long durationMs = cursor.getLong(iDur);

                boolean nameOk = isAudio(name);
                boolean mimeOk = (mime != null && mime.startsWith("audio/"));
                if (!mimeOk && !nameOk) { notAudio++; continue; }
                if (durationMs <= 500) { tooShort++; continue; }
                if (size <= 0) { zeroSize++; continue; }

                long id = cursor.getLong(iId);
                Uri item = Uri.withAppendedPath(base, String.valueOf(id));
                long startedAt = dateAddedSec > 0 ? dateAddedSec * 1000L : System.currentTimeMillis();

                out.add(new CallRecord(item, name, durationMs, startedAt));
                added++;
            }
            Log.i(TAG, "scanMediaStore useHints=" + useHints +
                    " added=" + added +
                    " skip{nullName=" + nullName +
                    ", notAudio=" + notAudio +
                    ", tooShort=" + tooShort +
                    ", zeroSize=" + zeroSize + "}");
        } finally {
            if (cursor != null) cursor.close();
        }
        return out;
    }

    private boolean containsHint(CallRecord r) {
        String n = r.fileName != null ? r.fileName.toLowerCase() : "";
        String p = r.uri.toString().toLowerCase();
        for (String h : HINTS) {
            String hh = h.toLowerCase();
            if (n.contains(hh) || p.contains(hh)) return true;
        }
        return false;
    }
    private boolean isAudio(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = fileName.substring(dot + 1).toLowerCase();
        return AUDIO_EXT.contains(ext);
    }


    // ========== CallRepository 구현 ==========

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
    @Override public synchronized void reload() {
        Log.i(TAG, "reload: force rescan");
        scanned = false;
        cache.clear();
        ensureScanned();
    }
}
