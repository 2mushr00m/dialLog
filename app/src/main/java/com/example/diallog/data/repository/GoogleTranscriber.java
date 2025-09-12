package com.example.diallog.data.repository;

import static okhttp3.MediaType.parse;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.example.diallog.BuildConfig;
import com.example.diallog.R;
import com.example.diallog.auth.AuthTokenProvider;
import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.network.ClovaSpeechApi;
import com.example.diallog.data.network.ClovaSpeechResponse;
import com.example.diallog.data.network.GoogleSttApi;
import com.example.diallog.data.network.GoogleSttRequest;
import com.example.diallog.data.network.GoogleSttResponse;
import com.example.diallog.utils.MediaResolver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.Retrofit;

public final class GoogleTranscriber implements Transcriber {

    private final Context app;
    private final Retrofit retrofit;
    private final AuthTokenProvider tokenProvider;
    private final String language;

    public GoogleTranscriber(Context app, Retrofit retrofit, AuthTokenProvider tokenProvider, String language) {
        this.app = app.getApplicationContext();
        this.retrofit = retrofit;
        this.tokenProvider = tokenProvider;
        this.language = language;
    }

    @Override public @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri) {
        try {
            // 1) 입력 파일 결정: 제공된 경로 우선, 없으면 데모용 raw 복사
            MediaResolver resolver = new MediaResolver(app);
            MediaResolver.ResolvedAudio ra = resolver.resolveWithFallback(audioUri, app.getResources(), R.raw.sample1, "sample1.mp3");

            // 2) multipart 파트 구성
            byte[] data;
            try (InputStream in = new FileInputStream(ra.file); ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    bout.write(buf, 0, n);
                }
                data = bout.toByteArray();
            }
            String b64 = Base64.encodeToString(data, Base64.NO_WRAP);

            GoogleSttRequest req = new GoogleSttRequest();
            req.config = new GoogleSttRequest.Config();
            req.config.languageCode = language;
            req.audio = new GoogleSttRequest.Audio();
            req.audio.content = b64;

            // 3) 호출
            GoogleSttApi api = retrofit.create(GoogleSttApi.class);
            Response<GoogleSttResponse> resp = api.recognize("Bearer " + tokenProvider.getToken(), req).execute();
            if (resp.code() == 401 || resp.code() == 403) {
                tokenProvider.invalidate();
                resp = api.recognize("Bearer " + tokenProvider.getToken(), req).execute();
            }
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new RuntimeException("Google STT failed: HTTP " + resp.code());
            }


            // 4) 매핑
            String transcript = "";
            GoogleSttResponse body = resp.body();
            if (body.results != null && !body.results.isEmpty()) {
                GoogleSttResponse.Result r0 = body.results.get(0);
                if (r0.alternatives != null && !r0.alternatives.isEmpty()) {
                    GoogleSttResponse.Alternative a0 = r0.alternatives.get(0);
                    if (a0.transcript != null) transcript = a0.transcript;
                }
            }

            List<TranscriptSegment> out = new ArrayList<>();
            out.add(new TranscriptSegment(transcript, 0, 0));

            if (ra.tempCopy) ra.file.delete();
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Google STT error: " + e.getMessage(), e);
        }
    }
}
