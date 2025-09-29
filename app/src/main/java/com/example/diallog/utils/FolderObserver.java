package com.example.diallog.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class FolderObserver {
    private final Context app;
    private final Uri treeUri;
    private final Runnable onChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ContentResolver cr;

    private @Nullable ContentObserver docObserver;
    private @Nullable ContentObserver mediaObserver;

    public FolderObserver(@NonNull Context appContext, @NonNull Uri treeUri, @NonNull Runnable onChange) {
        this.app = appContext.getApplicationContext();
        this.treeUri = treeUri;
        this.onChange = onChange;
        this.cr = app.getContentResolver();
    }

    @MainThread
    public void start() {
        stop();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri));
        docObserver = new ContentObserver(main) {
            @Override public void onChange(boolean selfChange, Uri uri) { trigger(); }
            @Override public void onChange(boolean selfChange) { trigger(); }
        };
        cr.registerContentObserver(children, true, docObserver);

        mediaObserver = new ContentObserver(main) {
            @Override public void onChange(boolean selfChange, Uri uri) { trigger(); }
            @Override public void onChange(boolean selfChange) { trigger(); }
        };
        cr.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
    }


    @MainThread
    public void stop() {
        if (docObserver != null) { cr.unregisterContentObserver(docObserver); docObserver = null; }
        if (mediaObserver != null) { cr.unregisterContentObserver(mediaObserver); mediaObserver = null; }
    }

    private void trigger() { onChange.run(); }
}
