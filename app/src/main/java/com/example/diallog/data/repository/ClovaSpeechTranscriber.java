package com.example.diallog.data.repository;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import static okhttp3.MediaType.parse;

import com.example.diallog.R;
import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.model.TranscriptionResult;
import com.example.diallog.data.network.ClovaSpeechResponse;
import com.example.diallog.data.network.ClovaSpeechApi;
import com.example.diallog.BuildConfig;
import com.example.diallog.utils.MediaResolver;

import java.util.ArrayList;
import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.Retrofit;

public final class ClovaSpeechTranscriber implements Transcriber {
    private static final String TAG = "ClovaTranscriber";
    private static final String LANGUAGE_CODE = "ko-KR";

    private final Context app;
    private final Retrofit retrofit;

    public ClovaSpeechTranscriber(@NonNull Context app, Retrofit retrofit) {
        this.app = app.getApplicationContext();
        this.retrofit = retrofit;
    }

    @Override
    public @NonNull TranscriptionResult transcribe(@NonNull Uri audioUri) {
        return transcribe(audioUri, LANGUAGE_CODE);
    }

    @Override
    public @NonNull TranscriptionResult transcribe(@NonNull Uri audioUri, @NonNull String languageCode) {
        List<TranscriptSegment> segments = transcribeInternal(audioUri, languageCode);
        return TranscriptionResult.finalResult(segments, null);
    }

    @NonNull
    private List<TranscriptSegment> transcribeInternal(@NonNull Uri audioUri, @NonNull String languageCode) {
        MediaResolver.ResolvedAudio resolved = null;
        Log.i(TAG, "transcribe: start uri=" + audioUri);
        try {
            // 1) 입력 파일 결정: 제공된 경로 우선, 없으면 데모용 raw 복사
            MediaResolver resolver = new MediaResolver(app);
            resolved = resolver.resolveWithFallback(audioUri, app.getResources(), R.raw.sample1, "sample1.mp3");
            Log.d(TAG, "transcribe: resolved file=" + resolved.file
                    + " size=" + resolved.file.length()
                    +" tempCopy=" + resolved.tempCopy);


            // 2) multipart 파트 구성
            RequestBody mediaRb = RequestBody.create(resolved.file, parse(MediaResolver.guessMimeType(resolved.file.getName())));
            MultipartBody.Part media = MultipartBody.Part.createFormData("media", resolved.file.getName(), mediaRb);

            String effectiveLanguage = TextUtils.isEmpty(languageCode) ? LANGUAGE_CODE : languageCode;
            String paramsJson =
                    "{" +
                            "\"language\":\"" + effectiveLanguage + "\"," +
                            "\"completion\":\"sync\"," +
                            "\"fullText\":true," +
                            "\"wordAlignment\":true," +
                            "\"diarization\":{\"enable\":true}" +
                            "}";
            RequestBody params = RequestBody.create(parse("application/json"), paramsJson);
            RequestBody type = RequestBody.create(parse("text/plain"), "application/json");

            // 3) 호출
            ClovaSpeechApi svc = retrofit.create(ClovaSpeechApi.class);
            Log.i(TAG, "transcribe: calling ClovaSpeech language=" + effectiveLanguage);
            Response<ClovaSpeechResponse> resp = svc.recognize(
                    BuildConfig.NAVER_CLOVA_STT_API_KEY, media, params, type
            ).execute();
            Log.i(TAG, "transcribe: response code=" + resp.code());

            if (!resp.isSuccessful() || resp.body() == null) {
                Log.e(TAG, "transcribe: request failed code=" + resp.code());
                throw new RuntimeException("ClovaSpeech failed: HTTP " + resp.code());
            }

            // 4) 매핑
            ClovaSpeechResponse b = resp.body();
            List<TranscriptSegment> out = new ArrayList<>();
            if (b.segments != null && !b.segments.isEmpty()) {
                Log.i(TAG, "transcribe: mapping " + b.segments.size() + " segments");
                for (ClovaSpeechResponse.Seg s : b.segments) {
                    out.add(new TranscriptSegment(s.text, s.startMs, s.endMs, 1.0));
                }
            } else if (b.text != null && !b.text.isEmpty()) {
                Log.i(TAG, "transcribe: single text fallback length=" + b.text.length());
                out.add(new TranscriptSegment(b.text, 0, 0, 1.0));
            }

            return out;
        } catch (Exception e) {
            Log.e(TAG, "transcribe: failed", e);
            throw new RuntimeException("ClovaSpeech error: " + e.getMessage(), e);
        } finally {
            if (resolved != null && resolved.tempCopy) {
                Log.d(TAG, "transcribe: deleting temp file=" + resolved.file);
                //noinspection ResultOfMethodCallIgnored
                resolved.file.delete();
            }
        }
    }
}
