package com.example.diallog.data.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import static okhttp3.MediaType.parse;

import com.example.diallog.R;
import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.model.TranscriptionResult;
import com.example.diallog.data.network.ClovaSpeechResponse;
import com.example.diallog.data.network.ClovaSpeechApi;
import com.example.diallog.BuildConfig;
import com.example.diallog.utils.AudioUriResolver;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.Retrofit;

public final class ClovaSpeechTranscriber implements Transcriber {
    private static final String TAG = "Clova";
    private static final String DEFAULT_LANGUAGE_CODE = "ko-KR";
    private final Transcriber fallback;

    private final Context app;
    private final Retrofit clovaApi;

    public ClovaSpeechTranscriber(@NonNull Context ctx, Retrofit api, Transcriber fallback) {
        this.app = ctx.getApplicationContext();
        this.clovaApi = api;
        this.fallback = fallback;
    }

    @Override
    public @NonNull TranscriptionResult transcribe(@NonNull Uri audioUri) {
        long t0 = System.nanoTime();
        try {
            List<TranscriptSegment> segments = transcribeInternal(audioUri);
            return TranscriptionResult.finalResult(segments, null);
        } catch (Exception e) {
            Log.e(TAG, "STT 실패: " + audioUri, e);
            if (fallback != null && fallback != this)
                return fallback.transcribe(audioUri);
            throw e;
        } finally {
            Log.i(TAG, "소요시간=" + (System.nanoTime()-t0)/1_000_000 + "ms");
        }
    }

    @NonNull
    private List<TranscriptSegment> transcribeInternal(@NonNull Uri audioUri) {
        Log.i(TAG, "STT 진행 시작: uri=" + audioUri);
        List<TranscriptSegment> ret = new ArrayList<>();
        AudioUriResolver.ResolvedAudio resolved = null;

        try {
            // 1) File Resolved
            AudioUriResolver resolver = new AudioUriResolver(app);
            resolved = resolver.resolveWithFallback(audioUri, app.getResources(), R.raw.sample1, "sample1.mp3");

            Log.d(TAG, "파일 해석: file=" + resolved.file
                    + " size=" + resolved.file.length()
                    + " tempCopy=" + resolved.tempCopy);


            // 2) multipart 파트 구성
            RequestBody mediaRb = RequestBody.create(resolved.file, parse(AudioUriResolver.guessMimeType(resolved.file.getName())));
            MultipartBody.Part media = MultipartBody.Part.createFormData("media", resolved.file.getName(), mediaRb);

            String paramsJson =
                    "{" +
                            "\"language\":\"" + DEFAULT_LANGUAGE_CODE + "\"," +
                            "\"completion\":\"sync\"," +
                            "\"fullText\":true," +
                            "\"wordAlignment\": false," +
                            "\"diarization\":{\"enable\":true}" +
                            "}";
            RequestBody params = RequestBody.create(parse("application/json"), paramsJson);
            RequestBody type = RequestBody.create(parse("text/plain"), "application/json");

            // 3) 호출
            ClovaSpeechApi svc = clovaApi.create(ClovaSpeechApi.class);
            Log.i(TAG, "호출: language=" + DEFAULT_LANGUAGE_CODE);
            Response<ClovaSpeechResponse> resp = svc.recognize(
                    BuildConfig.NAVER_CLOVA_STT_API_KEY, media, params, type
            ).execute();
            Log.i(TAG, "응답: response code=" + resp.code());

            if (!resp.isSuccessful() || resp.body() == null)
                Log.e(TAG, "응답 실패: HTTP " + resp.code());

            // 4) 매핑
            ClovaSpeechResponse b = resp.body();
            if (b != null && b.segments != null && !b.segments.isEmpty()) {
                Log.i(TAG, "매핑: segments " + b.segments.size() + "개");
                for (ClovaSpeechResponse.Seg s : b.segments) {
                    String speakerLabel = (s.speaker != null) ? s.speaker.label : null;
                    ret.add(new TranscriptSegment(s.text, s.startMs, s.endMs, s.confidence, speakerLabel));
                }
            } else if (b != null && b.text != null && !b.text.isEmpty()) {
                Log.i(TAG, "싱글 텍스트: 전체 텍스트 길이=" + b.text.length());
                ret.add(new TranscriptSegment(b.text, 0, 0, 1.0F, null));
            }

            return ret;
        } catch (IOException io) {
            Log.e(TAG, "네트워크 오류: " + io.getMessage());
        } catch (JsonParseException jp) {
            Log.w(TAG, "JSON 파싱 오류", jp);
            throw jp;
        } catch (Exception e) {
            Log.w(TAG, "알 수 없는 오류", e);
        } finally {
            if (resolved != null && resolved.tempCopy) {
                Log.d(TAG, "temp 제거: file=" + resolved.file);
                //noinspection ResultOfMethodCallIgnored
                resolved.file.delete();
            }
        }
        return ret;
    }
}
