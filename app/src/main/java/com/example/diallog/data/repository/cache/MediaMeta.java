package com.example.diallog.data.repository.cache;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

public class MediaMeta {
    final long size; final long lastModified; final long durationMs;
    MediaMeta(long size, long lastModified, long durationMs){ this.size=size; this.lastModified=lastModified; this.durationMs=durationMs; }

    static MediaMeta query(@NonNull Context ctx, @NonNull Uri uri) {
        String[] proj = { MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.Audio.Media.DURATION };
        try (Cursor c = ctx.getContentResolver().query(uri, proj, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                long size = getLong(c, MediaStore.MediaColumns.SIZE);
                long modS = getLong(c, MediaStore.MediaColumns.DATE_MODIFIED); // seconds
                long dur  = getLong(c, MediaStore.Audio.Media.DURATION);
                return new MediaMeta(size, modS>0?modS*1000L:0L, dur);
            }
        } catch (Exception ignore) {}
        return new MediaMeta(0,0,0);
    }
    private static long getLong(Cursor c, String col){ int i=c.getColumnIndex(col); return i>=0?c.getLong(i):0; }

}
