package com.example.diallog.data.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.example.diallog.R;
import com.example.diallog.auth.AuthTokenProvider;
import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.network.GoogleSttApi;
import com.example.diallog.data.network.GoogleSttRequest;
import com.example.diallog.data.network.GoogleSttResponse;
import com.example.diallog.utils.MediaResolver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;
import retrofit2.Retrofit;

public final class GoogleTranscriber implements Transcriber {

    private final Context app;
    private final GoogleSttApi api;
    private final AuthTokenProvider tokenProvider;
    private final String language;

    public GoogleTranscriber(Context app, Retrofit retrofit, AuthTokenProvider tokenProvider, String language) {
        this.app = app.getApplicationContext();
        this.api = retrofit.create(GoogleSttApi.class);
        this.tokenProvider = tokenProvider;
        this.language = language;
    }

    @Override
    public @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri) {
        return transcribe(audioUri, language);
    }

    @Override public @NonNull List<TranscriptSegment> transcribe(@NonNull Uri audioUri, @NonNull String languageCode) {
        MediaResolver.ResolvedAudio resolved = null;
        try {
            // 1) 입력 파일 결정: 제공된 경로 우선, 없으면 데모용 raw 복사
            MediaResolver resolver = new MediaResolver(app);
            resolved = resolver.resolveWithFallback(audioUri, app.getResources(), R.raw.sample1, "sample1.mp3");

            byte[] audioBytes = readFile(resolved.file);
            String base64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP);

            GoogleSttRequest request = buildRequest(base64);


            Response<GoogleSttResponse> response = executeRecognize(request);
            if (response.code() == 401 || response.code() == 403) {
                tokenProvider.invalidate();
                response = executeRecognize(request);
            }

            if (!response.isSuccessful() || response.body() == null) {
                String errorMessage = extractError(response);
                throw new IOException("Google STT failed: HTTP " + response.code() + errorMessage);
            }

            String transcript = extractTranscript(response.body());
            List<TranscriptSegment> segments = new ArrayList<>();
            segments.add(new TranscriptSegment(transcript, 0, 0));
            return segments;
        } catch (IOException ioe) {
            throw new RuntimeException("Google STT I/O error: " + ioe.getMessage(), ioe);
        } catch (Exception e) {
            throw new RuntimeException("Google STT error: " + e.getMessage(), e);
        } finally {
            if (resolved != null && resolved.tempCopy) {
                //noinspection ResultOfMethodCallIgnored
                resolved.file.delete();
            }
        }
    }

    private Response<GoogleSttResponse> executeRecognize(GoogleSttRequest request) throws Exception {
        String token = tokenProvider.getToken();
        return api.recognize("Bearer " + token, request).execute();
    }

    private GoogleSttRequest buildRequest(String base64Content) {
        GoogleSttRequest request = new GoogleSttRequest();
        GoogleSttRequest.Config config = new GoogleSttRequest.Config();
        config.encoding = "LINEAR16";
        config.sampleRateHertz = 16000;
        config.languageCode = language;
        config.enableAutomaticPunctuation = true;
        request.config = config;

        GoogleSttRequest.Audio audio = new GoogleSttRequest.Audio();
        audio.content = base64Content;
        request.audio = audio;
        return request;
    }

    private static String extractTranscript(GoogleSttResponse body) {
        if (body == null || body.results == null || body.results.isEmpty()) {
            return "";
        }
        GoogleSttResponse.Result firstResult = body.results.get(0);
        if (firstResult == null || firstResult.alternatives == null || firstResult.alternatives.isEmpty()) {
            return "";
        }
        GoogleSttResponse.Alternative firstAlternative = firstResult.alternatives.get(0);
        return firstAlternative != null && firstAlternative.transcript != null
                ? firstAlternative.transcript
                : "";
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static String extractError(Response<GoogleSttResponse> response) {
        try {
            if (response.errorBody() == null) {
                return "";
            }
            String body = response.errorBody().string();
            return body == null || body.isEmpty() ? "" : " - " + body;
        } catch (IOException e) {
            return "";
        }
    }
}
