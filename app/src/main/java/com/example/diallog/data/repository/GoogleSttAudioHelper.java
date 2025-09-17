package com.example.diallog.data.repository;

import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.data.network.GoogleSttRequest;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

final class GoogleSttAudioHelper {
    private static final String TAG = "GoogleSttAudioCfg";
    private static final int FALLBACK_SAMPLE_RATE_HZ = 16_000;

    private GoogleSttAudioHelper() {
    }

    static void applyEncoding(@NonNull GoogleSttRequest.Config config,
                              @Nullable String mimeType,
                              int sampleRateHz) {
        EncodingAttributes attrs = inferEncodingAttributes(mimeType);
        if (attrs == null) {
            if (!TextUtils.isEmpty(mimeType)) {
                Log.d(TAG, "applyEncoding: unsupported mimeType=" + mimeType);
            }
            return;
        }

        config.encoding = attrs.encoding;
        if (attrs.requiresSampleRate) {
            int effectiveRate = sampleRateHz > 0 ? sampleRateHz : FALLBACK_SAMPLE_RATE_HZ;
            if (sampleRateHz <= 0) {
                Log.d(TAG, "applyEncoding: using fallback sample rate for mimeType=" + mimeType);
            }
            config.sampleRateHertz = effectiveRate;
        }
    }

    static int extractSampleRateHz(@NonNull File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
            if (!TextUtils.isEmpty(sampleRateStr)) {
                return Integer.parseInt(sampleRateStr);
            }
        } catch (Exception e) {
            Log.w(TAG, "extractSampleRateHz: failed to read metadata", e);
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return -1;
    }

    @Nullable
    private static EncodingAttributes inferEncodingAttributes(@Nullable String mimeType) {
        if (TextUtils.isEmpty(mimeType)) {
            return null;
        }
        String lower = mimeType.toLowerCase(Locale.US);
        if (lower.contains("mpeg") || lower.contains("mp3")) {
            return new EncodingAttributes("MP3", false);
        }
        if (lower.contains("wav")) {
            return new EncodingAttributes("LINEAR16", true);
        }
        if (lower.contains("ogg") || lower.contains("opus")) {
            return new EncodingAttributes("OGG_OPUS", false);
        }
        if (lower.contains("amr-wb") || lower.contains("3gpp2")) {
            return new EncodingAttributes("AMR_WB", false);
        }
        if (lower.contains("amr") || lower.contains("3gpp")) {
            return new EncodingAttributes("AMR", false);
        }
        if (lower.contains("flac")) {
            return new EncodingAttributes("FLAC", true);
        }
        return null;
    }

    private static final class EncodingAttributes {
        final String encoding;
        final boolean requiresSampleRate;

        EncodingAttributes(String encoding, boolean requiresSampleRate) {
            this.encoding = encoding;
            this.requiresSampleRate = requiresSampleRate;
        }
    }
}