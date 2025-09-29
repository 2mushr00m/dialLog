package com.example.diallog.utils;


import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public final class PermissionHelper {
    public static final int REQ_READ_AUDIO = 1001;

    @NonNull public static String requiredPermission() {
        if (Build.VERSION.SDK_INT >= 33) return Manifest.permission.READ_MEDIA_AUDIO;
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean hasReadAudioPermission(@NonNull Activity act) {
        return ContextCompat.checkSelfPermission(act, requiredPermission())
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestReadAudio(@NonNull Activity act) {
        ActivityCompat.requestPermissions(act, new String[]{requiredPermission()}, REQ_READ_AUDIO);
    }

}
