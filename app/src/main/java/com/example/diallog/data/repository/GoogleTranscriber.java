package com.example.diallog.data.repository;

import static okhttp3.MediaType.parse;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.diallog.BuildConfig;
import com.example.diallog.R;
import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.network.ClovaSpeechApi;
import com.example.diallog.data.network.ClovaSpeechResponse;
import com.example.diallog.data.network.GoogleSttApi;
import com.example.diallog.data.network.GoogleSttResponse;
import com.example.diallog.utils.MediaResolver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.Retrofit;

public final class GoogleTranscriber implements Transcriber {

    private final Context app;
    private final Retrofit retrofit;
    private final String language;

    public GoogleTranscriber(Context app, Retrofit retrofit, String language) {
        this.app = app.getApplicationContext();
        this.retrofit = retrofit;
        this.language = language;
    }

    @Override public @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri) {
        try {
            // 1) 입력 파일 결정: 제공된 경로 우선, 없으면 데모용 raw 복사
            MediaResolver resolver = new MediaResolver(app);
            MediaResolver.ResolvedAudio ra = resolver.resolveWithFallback(audioUri, app.getResources(), R.raw.sample1, "sample1.mp3");

            // 2) multipart 파트 구성
            RequestBody mediaRb = RequestBody.create(ra.file, parse(MediaResolver.guessMimeType(ra.file.getName())));
            MultipartBody.Part media = MultipartBody.Part.createFormData("media", ra.file.getName(), mediaRb);
            RequestBody lang = RequestBody.create(language, parse("text/plain"));
            RequestBody enableWordTime = RequestBody.create("true", parse("text/plain"));

            // 3) 호출
            GoogleSttApi api = retrofit.create(GoogleSttApi.class);
            Response<GoogleSttResponse> resp = api.recognize(media, lang, enableWordTime).execute();
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new RuntimeException("Google STT failed: HTTP " + resp.code());
            }

            // 4) 매핑
            GoogleSttResponse body = resp.body();
            List<TranscriptSegment> out = new ArrayList<>();
            if (body.segments != null && !body.segments.isEmpty()) {
                for (GoogleSttResponse.Seg s : body.segments) {
                    out.add(new TranscriptSegment(s.text != null ? s.text : "", s.startMs, s.endMs));
                }
            } else if (body.text != null && !body.text.isEmpty()) {
                out.add(new TranscriptSegment(body.text, 0, 0));
            }

            if (ra.tempCopy)
                ra.file.delete();

            return out;
        } catch (Exception e) {
            throw new RuntimeException("Google STT error: " + e.getMessage(), e);
        }
    }
}
