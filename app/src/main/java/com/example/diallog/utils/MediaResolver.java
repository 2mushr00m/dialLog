// utils/MediaInputResolver.java
package com.example.diallog.utils;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.annotation.*;
import java.io.*;
import java.util.Objects;

public final class MediaResolver {
    public static final class ResolvedAudio {
        public final File file; public final String mime; public final boolean tempCopy;
        ResolvedAudio(File f, String m, boolean t) { this.file=f; this.mime=m; this.tempCopy=t; }
    }

    private final Context app;
    public MediaResolver(@NonNull Context context) { this.app = context.getApplicationContext(); }

    public @NonNull ResolvedAudio resolve(@NonNull Uri uri) throws Exception {
        return resolveInternal(uri, null, null);
    }
    public @NonNull ResolvedAudio resolveWithFallback(@NonNull Uri uri, @NonNull Resources res, int rawId, @NonNull String name) throws Exception {
        return resolveInternal(uri, new Fallback(res, rawId, name), null);
    }

    private static final class Fallback { final Resources r; final int id; final String name; Fallback(Resources r,int id,String n){this.r=r;this.id=id;this.name=n;} }

    private @NonNull ResolvedAudio resolveInternal(@NonNull Uri uri, @Nullable Fallback fb, @Nullable String forceName) throws Exception {
        String scheme = uri.getScheme();
        if ("content".equalsIgnoreCase(scheme)) {
            String name = queryName(uri);
            File f = copyContentToCache(uri, safeName(name));
            return new ResolvedAudio(f, guessMimeType(f.getName()), true);
        }
        if ("file".equalsIgnoreCase(scheme)) {
            File f = new File(Objects.requireNonNull(uri.getPath()));
            if (f.exists() && f.length() > 0) return new ResolvedAudio(f, guessMimeType(f.getName()), false);
        }
        if (scheme == null || scheme.isEmpty()) {
            File f = new File(uri.toString());
            if (f.exists() && f.length() > 0) return new ResolvedAudio(f, guessMimeType(f.getName()), false);
        }
        if (fb != null) {
            File f = copyRawToCache(fb.r, app.getCacheDir(), fb.id, fb.name);
            return new ResolvedAudio(f, guessMimeType(f.getName()), true);
        }
        throw new IllegalStateException("Unable to resolve: " + uri);
    }

    private @Nullable String queryName(@NonNull Uri uri) {
        String[] proj = { MediaStore.MediaColumns.DISPLAY_NAME };
        try (Cursor c = app.getContentResolver().query(uri, proj, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignore) {}
        return null;
    }
    private @NonNull String safeName(@Nullable String n) { return (n!=null && !n.isEmpty()) ? n : "audio_"+System.currentTimeMillis()+".bin"; }
    private static @NonNull File copyRawToCache(@NonNull Resources res, @NonNull File cache, int id, @NonNull String name) throws Exception {
        File dst = new File(cache, name);
        try (InputStream in = res.openRawResource(id); FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) out.write(buf,0,n);
        }
        return dst;
    }
    private @NonNull File copyContentToCache(@NonNull Uri uri, @NonNull String name) throws Exception {
        File out = new File(app.getCacheDir(), name);
        try (InputStream in = app.getContentResolver().openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IllegalStateException("cannot open: " + uri);
            byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) os.write(buf,0,n);
        }
        return out;
    }
    public static @NonNull String guessMimeType(@NonNull String fileName) {
        String s = fileName.toLowerCase();
        if (s.endsWith(".mp3")) return "audio/mpeg";
        if (s.endsWith(".wav")) return "audio/wav";
        if (s.endsWith(".m4a")) return "audio/mp4";
        if (s.endsWith(".aac")) return "audio/aac";
        if (s.endsWith(".ogg")) return "audio/ogg";
        if (s.endsWith(".amr")) return "audio/amr";
        return "application/octet-stream";
    }
}
