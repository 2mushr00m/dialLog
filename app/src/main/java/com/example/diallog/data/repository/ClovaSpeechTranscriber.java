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
            AudioUriResolver resolver = new AudioUriResolver(app);
            resolved = resolver.resolveWithFallback(audioUri, app.getResources(), R.raw.sample1, "sample1.mp3");

            Log.d(TAG, "transcribe: resolved file=" + resolved.file
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
                            "\"wordAlignment\":true," +
                            "\"diarization\":{\"enable\":true}" +
                            "}";
            RequestBody params = RequestBody.create(parse("application/json"), paramsJson);
            RequestBody type = RequestBody.create(parse("text/plain"), "application/json");

            // 3) 호출
            ClovaSpeechApi svc = clovaApi.create(ClovaSpeechApi.class);
            Log.i(TAG, "transcribe: calling ClovaSpeech language=" + DEFAULT_LANGUAGE_CODE);
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
            if (b.segments != null && !b.segments.isEmpty()) {
                Log.i(TAG, "transcribe: mapping " + b.segments.size() + " segments");
                for (ClovaSpeechResponse.Seg s : b.segments) {
                    ret.add(new TranscriptSegment(s.text, s.startMs, s.endMs, 1.0));
                }
            } else if (b.text != null && !b.text.isEmpty()) {
                Log.i(TAG, "transcribe: single text fallback length=" + b.text.length());
                ret.add(new TranscriptSegment(b.text, 0, 0, 1.0));
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
                Log.d(TAG, "temp 제거 file=" + resolved.file);
                //noinspection ResultOfMethodCallIgnored
                resolved.file.delete();
            }
        }
        return ret;
    }
}
