package com.example.diallog.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import static okhttp3.MediaType.parse;

import com.example.diallog.R;
import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.network.ClovaSpeechResponse;
import com.example.diallog.data.network.ClovaSpeechApi;
import com.example.diallog.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.Retrofit;

public final class ClovaSpeechTranscriber implements Transcriber {
    private final Context app;
    private final Retrofit retrofit;
    private final String language;

    public ClovaSpeechTranscriber(@NonNull Context app, Retrofit retrofit, @NonNull String language) {
        this.app = app.getApplicationContext();
        this.retrofit = retrofit;
        this.language = language;
    }

    @Override
    public List<TranscriptSegment> transcribe(String audioPath) {
        try {
            // 1) 입력 파일 결정: 제공된 경로 우선, 없으면 데모용 raw 복사
            File mediaFile = resolveInputFile(audioPath);

            // 2) multipart 파트 구성
            RequestBody mediaRb = RequestBody.create(mediaFile, parse(guessMimeType(mediaFile.getName())));
            MultipartBody.Part media = MultipartBody.Part.createFormData("media", mediaFile.getName(), mediaRb);

            String paramsJson = "{\"language\":\"" + language + "\",\"completion\":\"sync\",\"fullText\":true}";
            RequestBody params = RequestBody.create(parse("application/json"), paramsJson);
            RequestBody type = RequestBody.create(parse("text/plain"), "application/json");

            // 3) 호출
            ClovaSpeechApi svc = retrofit.create(ClovaSpeechApi.class);
            Response<ClovaSpeechResponse> resp = svc.recognize(
                    BuildConfig.STT_API_KEY, media, params, type
            ).execute();

            if (!resp.isSuccessful() || resp.body() == null) {
                throw new RuntimeException("STT failed: HTTP " + resp.code());
            }

            // 4) 매핑
            ClovaSpeechResponse b = resp.body();
            List<TranscriptSegment> out = new ArrayList<>();
            if (b.segments != null && !b.segments.isEmpty()) {
                for (ClovaSpeechResponse.Seg s : b.segments) {
                    if (s == null || s.text == null) continue;
                    out.add(new TranscriptSegment(s.text, s.startMs, s.endMs));
                }
            } else if (b.text != null && !b.text.isEmpty()) {
                out.add(new TranscriptSegment(b.text, 0, 0));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("ClovaSpeech error: " + e.getMessage(), e);
        }
    }

    private File resolveInputFile(String audioPath) throws Exception {
        if (audioPath != null) {
            File f = new File(audioPath);
            if (f.exists() && f.length() > 0) return f;
        }
        // 데모용: res/raw/sample1 → cache 복사
        return copyRawToCache(R.raw.sample1, "sample1.mp3");
    }

    private String guessMimeType(@NonNull String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp3")) return "audio/mp3";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        return "application/octet-stream";
    }

    private File copyRawToCache(int resId, String fileName) throws Exception {
        File dst = new File(app.getCacheDir(), fileName);
        try (InputStream in = app.getResources().openRawResource(resId);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        return dst;
    }

}
