package com.example.diallog.utils;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class SampleAudioSeeder {
    public static void seed(Context ctx) throws IOException {
        File dir = new File(Environment.getExternalStorageDirectory(), "Recordings");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs failed: " + dir);
        seedInto(ctx, dir);
    }

    public static void seedInto(Context ctx, File dir) throws IOException {
        // 1) 날짜 포함(최신)
        File f1 = new File(dir, "20250911_101500_call_alice.wav");
        // 2) 날짜 포함(과거)
        File f2 = new File(dir, "20250910_220000_call_bob.wav");
        // 3) 날짜 없이
        File f3 = new File(dir, "no_date_sample.wav");

        writeSilentWav(f1, 1, 16000, 1); // 1sec, 16kHz, mono
        writeSilentWav(f2, 1, 16000, 1);
        writeSilentWav(f3, 1, 16000, 1);

        // MediaStore 인덱싱
        String[] paths = new String[]{ f1.getAbsolutePath(), f2.getAbsolutePath(), f3.getAbsolutePath() };
        MediaScannerConnection.scanFile(ctx.getApplicationContext(), paths, new String[]{"audio/wav","audio/wav","audio/wav"}, null);
    }
    private static void writeSilentWav(File out, int seconds, int sampleRate, int channels) throws IOException {
        int bitsPerSample = 16;
        int totalSamples = seconds * sampleRate * channels;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int dataSize = totalSamples * bitsPerSample / 8;
        int chunkSize = 36 + dataSize;

        try (FileOutputStream fos = new FileOutputStream(out)) {
            // RIFF 헤더
            fos.write(new byte[]{'R','I','F','F'});
            writeLE32(fos, chunkSize);
            fos.write(new byte[]{'W','A','V','E'});

            // fmt chunk
            fos.write(new byte[]{'f','m','t',' '});
            writeLE32(fos, 16);                 // Subchunk1Size
            writeLE16(fos, 1);                  // PCM
            writeLE16(fos, (short) channels);
            writeLE32(fos, sampleRate);
            writeLE32(fos, byteRate);
            writeLE16(fos, (short) (channels * bitsPerSample / 8)); // BlockAlign
            writeLE16(fos, (short) bitsPerSample);

            // data chunk
            fos.write(new byte[]{'d','a','t','a'});
            writeLE32(fos, dataSize);

            // 무음 샘플
            byte[] frame = new byte[2]; // 16-bit 0
            int frames = totalSamples;
            for (int i = 0; i < frames; i++) fos.write(frame);
        }
    }

    private static void writeLE16(FileOutputStream fos, int v) throws IOException {
        fos.write(new byte[]{ (byte)(v & 0xFF), (byte)((v >> 8) & 0xFF) });
    }
    private static void writeLE32(FileOutputStream fos, int v) throws IOException {
        fos.write(new byte[]{
                (byte)(v & 0xFF),
                (byte)((v >> 8) & 0xFF),
                (byte)((v >> 16) & 0xFF),
                (byte)((v >> 24) & 0xFF)
        });
    }

}
