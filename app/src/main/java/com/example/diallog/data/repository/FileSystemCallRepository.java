package com.example.diallog.data.repository;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.example.diallog.data.model.CallRecord;
import com.example.diallog.utils.FolderObserver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class FileSystemCallRepository implements CallRepository {
    private static final String TAG = "FS Repo";

    private final Context app;
    private final ContentResolver cr;
    private final Runnable onDataChanged;
    private final Executor io = Executors.newSingleThreadExecutor();
    private final List<CallRecord> cache = new ArrayList<>();

    @Nullable private Uri treeUri = null;
    @Nullable private FolderObserver folderObserver;
    @Nullable private ContentObserver mediaObserver;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean scanning = false;


    private static final Set<String> EXTS = new HashSet<>(Arrays.asList(
            "m4a", "mp3", "aac", "wav", "3gp", "amr", "ogg", "flac"
    ));
    private static final Set<String> HINTS = new HashSet<>(Arrays.asList(
            "Call", "Recorder", "record", "통화", "녹음", "CallRec", "CallRecord", "DialLog"
    ));

    public FileSystemCallRepository(@NonNull Context appContext, @NonNull Runnable onDataChanged) {
        this.app = appContext.getApplicationContext();
        this.cr = app.getContentResolver();
        this.onDataChanged = onDataChanged;
    }


    // ===== CallRepository =====

    @Override
    public synchronized List<CallRecord> getRecent(int offset, int limit) {
        int n = cache.size();
        if (offset >= n) return Collections.emptyList();
        int to = Math.min(n, offset + limit);
        return new ArrayList<>(cache.subList(offset, to));
    }

    @Override
    public @Nullable CallRecord getByUri(@NonNull Uri uri) {
        synchronized (this) {
            for (CallRecord r : cache) {
                if (r.uri != null && r.uri.equals(uri)) return r;
            }
        }
        return null;
    }

    @Override
    public void setTreeUri(@Nullable Uri uri) {
        this.treeUri = uri;
        startWatching();
        reload();
    }

    @NonNull @Override
    public Future<?> refreshAsync() {
        CompletableFuture<Void> f = new CompletableFuture<>();
        io.execute(() -> {
            try { rescan(); f.complete(null); }
            catch (Throwable t) { f.completeExceptionally(t); }
        });
        return f;
    }

    @Override
    public void ensureScanned() {
        if (cache.isEmpty()) reload();
    }

    @Override
    public void reload() {
        if (scanning) return;
        scanning = true;
        io.execute(() -> {
            try { rescan(); }
            finally { scanning = false; }
        });
    }


    // ===== Observers =====

    @MainThread
    public void startWatching() {
        stopWatching();
        if (treeUri == null) {
            mediaObserver = new ContentObserver(main) {
                @Override public void onChange(boolean selfChange, @Nullable Uri uri) { reload(); }
                @Override public void onChange(boolean selfChange) { reload(); }
            };
            cr.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
            cr.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, mediaObserver);
        } else {
            folderObserver = new FolderObserver(app, treeUri, this::reload);
            folderObserver.start();
        }
    }

    @MainThread
    public void stopWatching() {
        if (mediaObserver != null) { cr.unregisterContentObserver(mediaObserver); mediaObserver = null; }
        if (folderObserver != null) { folderObserver.stop(); folderObserver = null; }
    }


    // ===== Internal =====
    private void rescan() {
        List<CallRecord> fresh = (treeUri == null) ? scanMediaStore() : scanTree(treeUri);
        fresh.sort(Comparator.comparingLong((CallRecord c) -> c.startedAtEpochMs).reversed());
        synchronized (this) {
            cache.clear();
            cache.addAll(fresh);
        }
        onDataChanged.run();
    }

    private @NonNull List<CallRecord> scanMediaStore() {
        List<CallRecord> out = new ArrayList<>();
        // Audio와 Files 간 중복 방지를 위한 키(RELATIVE_PATH + DISPLAY_NAME)
        HashSet<String> seen = new HashSet<>();

        // 1) Audio 테이블 우선 수집
        final Uri audioUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        final String[] projAudio = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_ADDED
        };
        final String selCommon = MediaStore.Audio.Media.IS_PENDING + "=0";
        final String order = MediaStore.Audio.Media.DATE_MODIFIED + " DESC";

        try (Cursor c = cr.query(audioUri, projAudio, selCommon, null, order)) {
            if (c != null) {
                int idIdx   = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int nameIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                int relIdx  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH);
                int durIdx  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int modIdx  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);
                int addIdx  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);

                while (c.moveToNext()) {
                    String name = c.getString(nameIdx);
                    if (!isAudioByName(name)) continue;

                    String rel = c.getString(relIdx);
                    String key = makeKey(rel, name);
                    if (!seen.add(key)) continue;

                    long id = c.getLong(idIdx);
                    long durationMs = Math.max(0, c.getLong(durIdx));
                    long epochMs = safeEpochMs(c.getLong(modIdx), c.getLong(addIdx));

                    Uri uri = ContentUris.withAppendedId(audioUri, id);
                    out.add(new CallRecord(uri, name, durationMs, epochMs));
                }
            }
        } catch (Throwable ignore) {}

        // 2) Files 테이블 폴백(일부 기기에서 m4a가 Audio에 안 잡히는 케이스 보강)
        final Uri filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        final String[] projFiles = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DURATION,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATE_ADDED
        };
        final String selFiles = MediaStore.Files.FileColumns.IS_PENDING + "=0 AND " +
                MediaStore.Files.FileColumns.MEDIA_TYPE + "=?";
        final String[] argsFiles = { String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO) };

        try (Cursor c = cr.query(filesUri, projFiles, selFiles, argsFiles, order)) {
            if (c != null) {
                int idIdx   = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                int nameIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int relIdx  = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH);
                int durIdx  = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION);
                int modIdx  = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED);
                int addIdx  = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED);

                while (c.moveToNext()) {
                    String name = c.getString(nameIdx);
                    if (!isAudioByName(name)) continue;

                    String rel = c.getString(relIdx);
                    String key = makeKey(rel, name);
                    if (!seen.add(key)) continue;

                    long id = c.getLong(idIdx);
                    long durationMs = Math.max(0, c.getLong(durIdx));
                    long epochMs = safeEpochMs(c.getLong(modIdx), c.getLong(addIdx));

                    Uri uri = ContentUris.withAppendedId(filesUri, id);
                    if (durationMs <= 0) {
                        durationMs = probeDuration(uri);
                    }
                    out.add(new CallRecord(uri, name, durationMs, epochMs));
                }
            }
        } catch (Throwable ignore) {}

        return out;
    }
    private @NonNull List<CallRecord> scanTree(@NonNull Uri tree) {
        List<CallRecord> out = new ArrayList<>();
        DocumentFile root = DocumentFile.fromTreeUri(app, tree);
        if (root == null || !root.isDirectory()) return out;

        Deque<DocumentFile> dq = new ArrayDeque<>();
        dq.add(root);
        while (!dq.isEmpty()) {
            DocumentFile dir = dq.removeFirst();
            DocumentFile[] children = dir.listFiles();
            if (children == null) continue;
            for (DocumentFile f : children) {
                if (f.isDirectory()) {
                    dq.add(f);
                } else if (isAudioByName(f.getName())) {
                    CallRecord r = new CallRecord(f.getUri(), f.getName(), probeDuration(f.getUri()), f.lastModified());
                    if (r != null) out.add(r);
                }
            }
        }
        return out;
    }

    @NonNull
    private static String makeKey(@Nullable String relPath, @NonNull String fileName) {
        String rel = relPath != null ? relPath : "";
        return (rel + "|" + fileName).toLowerCase(Locale.ROOT);
    }

    private static long safeEpochMs(long dateModifiedSec, long dateAddedSec) {
        long tsSec = dateModifiedSec > 0 ? dateModifiedSec : dateAddedSec;
        return tsSec > 0 ? tsSec * 1000L : System.currentTimeMillis();
    }

    private boolean isAudioByName(@Nullable String name) {
        if (name == null) return false;
        int p = name.lastIndexOf('.');
        if (p < 0) return false;
        String ext = name.substring(p + 1).toLowerCase(Locale.ROOT);
        return EXTS.contains(ext);
    }
    private long probeDuration(@NonNull Uri uri) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(app, uri);
            String s = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (s != null) return Long.parseLong(s);
        } catch (Throwable ignore) {
        } finally {
            try { mmr.release(); } catch (Throwable ignored) {}
        }
        return 0L;
    }
}
