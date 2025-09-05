package com.example.diallog.utils;


import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public final class PermissionHelper {
    public static final int REQ_READ_AUDIO = 100;

    private PermissionHelper(){}

    public static boolean hasAudioRead(Activity a){
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(a, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(a, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    public static void requestAudioRead(Activity a){
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(a,
                    new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQ_READ_AUDIO);
        } else {
            ActivityCompat.requestPermissions(a,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_READ_AUDIO);
        }
    }
}
