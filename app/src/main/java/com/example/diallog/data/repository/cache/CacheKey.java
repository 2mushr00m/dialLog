package com.example.diallog.data.repository.cache;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.MessageDigest;

final class CacheKey {
    static String of(@NonNull Context ctx, @NonNull Uri uri) {
        MediaMeta m = MediaMeta.query(ctx, uri);
        String base = uri.toString() + "|" + m.size + "|" + m.lastModified + "|" + m.durationMs;
        return sha1(base);
    }
    static String stableKey(@NonNull Context ctx, @NonNull Uri uri){
        String id = queryColumn(ctx, uri, MediaStore.MediaColumns._ID);
        MediaMeta m = MediaMeta.query(ctx, uri);
        String base = (id!=null?("mid="+id):("u="+uri)) + "|" + m.size + "|" + m.lastModified + "|" + m.durationMs;
        return sha1(base);
    }
    public static @Nullable String queryColumn(@NonNull Context ctx, @NonNull Uri uri, @NonNull String column) {
        String[] proj = { column };
        try (Cursor c = ctx.getContentResolver().query(uri, proj, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(column);
                if (i >= 0 && !c.isNull(i)) return c.getString(i);
            }
        } catch (Throwable ignore) {}
        return null;
    }
    private static @NonNull String sha1(@NonNull String s){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length*2);
            for(byte b: d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch(Exception e){ return Integer.toHexString(s.hashCode()); }
    }
}