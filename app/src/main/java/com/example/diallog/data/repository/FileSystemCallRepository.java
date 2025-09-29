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

        String[] proj = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_ADDED
        };
        String sel = MediaStore.Audio.Media.IS_PENDING + "=0";
        String order = MediaStore.Audio.Media.DATE_MODIFIED + " DESC";

        Uri[] volumes = {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                MediaStore.Audio.Media.getContentUri("external_primary")
        };

        for (Uri vol : volumes) {
            try (Cursor c = cr.query(vol, proj, sel, null, order)) {
                if (c == null) continue;

                int idIdx   = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int nameIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                int durIdx  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int modIdx  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);
                int addIdx  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);

                while (c.moveToNext()) {
                    long id = c.getLong(idIdx);
                    String fileName = c.getString(nameIdx);
                    if (!isAudioByName(fileName)) continue;

                    long durationMs = Math.max(0, c.getLong(durIdx));
                    long tsSec = c.getLong(modIdx); if (tsSec == 0) tsSec = c.getLong(addIdx);
                    long epochMs = tsSec > 0 ? tsSec * 1000L : System.currentTimeMillis();

                    Uri uri = ContentUris.withAppendedId(vol, id);

                    CallRecord r = new CallRecord(uri, fileName, durationMs, epochMs);
                    out.add(r);
                }
            } catch (Throwable ignore) {}
        }

        out.addAll(scanFilesBackup());
        return out;
    }
    private @NonNull List<CallRecord> scanFilesBackup() {
        List<CallRecord> out = new ArrayList<>();
        Uri files = MediaStore.Files.getContentUri("external");
        String[] proj = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATE_ADDED
        };
        String sel = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" +
                MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO + " AND " +
                MediaStore.Files.FileColumns.IS_PENDING + "=0";
        String order = MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC";

        try (Cursor c = cr.query(files, proj, sel, null, order)) {
            if (c == null) return out;

            int idIdx   = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            int nameIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
            int modIdx  = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED);
            int addIdx  = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED);

            while (c.moveToNext()) {
                long id = c.getLong(idIdx);
                String fileName = c.getString(nameIdx);
                if (!isAudioByName(fileName)) continue;

                long tsSec = c.getLong(modIdx);
                if (tsSec == 0) tsSec = c.getLong(addIdx);
                long epochMs = tsSec > 0 ? tsSec * 1000L : System.currentTimeMillis();

                Uri uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id);

                CallRecord r = new CallRecord(uri, fileName, probeDuration(uri), epochMs);
                out.add(r);
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
