package com.example.diallog.data.repository;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import static okhttp3.MediaType.parse;

import com.example.diallog.R;
import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.network.ClovaSpeechResponse;
import com.example.diallog.data.network.ClovaSpeechApi;
import com.example.diallog.BuildConfig;
import com.example.diallog.utils.MediaResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
    public @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri) {
        MediaResolver.ResolvedAudio resolved = null;
        try {
            // 1) 입력 파일 결정: 제공된 경로 우선, 없으면 데모용 raw 복사
            MediaResolver resolver = new MediaResolver(app);
            resolved = resolver.resolveWithFallback(audioUri, app.getResources(), R.raw.sample1, "sample1.mp3");

            // 2) multipart 파트 구성
            RequestBody mediaRb = RequestBody.create(resolved.file, parse(
                    MediaResolver.guessMimeType(resolved.file.getName())));
            MultipartBody.Part media = MultipartBody.Part.createFormData("media", resolved.file.getName(), mediaRb);

            String paramsJson =
                    "{"
                            + "\"language\":\"" + language + "\","
                            + "\"completion\":\"sync\","
                            + "\"fullText\":true,"
                            + "\"wordAlignment\":true,"
                            + "\"diarization\":{\"enable\":true}"
                            + "}";
            RequestBody params = RequestBody.create(parse("application/json"), paramsJson);
            RequestBody type = RequestBody.create(parse("text/plain"), "application/json");

            // 3) 호출
            ClovaSpeechApi svc = retrofit.create(ClovaSpeechApi.class);
            Response<ClovaSpeechResponse> resp = svc.recognize(
                    BuildConfig.NAVER_CLOVA_STT_API_KEY, media, params, type
            ).execute();

            if (!resp.isSuccessful() || resp.body() == null) {
                throw new RuntimeException("ClovaSpeech failed: HTTP " + resp.code());
            }

            // 4) 매핑
            ClovaSpeechResponse b = resp.body();
            List<TranscriptSegment> out = new ArrayList<>();
            if (b.segments != null && !b.segments.isEmpty()) {
                for (ClovaSpeechResponse.Seg s : b.segments) {
                    out.add(new TranscriptSegment(s.text, s.startMs, s.endMs));
                }
            } else if (b.text != null && !b.text.isEmpty()) {
                out.add(new TranscriptSegment(b.text, 0, 0));
            }

            return out;
        } catch (Exception e) {
            throw new RuntimeException("ClovaSpeech error: " + e.getMessage(), e);
        } finally {
            if (resolved != null && resolved.tempCopy) {
                //noinspection ResultOfMethodCallIgnored
                resolved.file.delete();
            }
        }
    }
}
