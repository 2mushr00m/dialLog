package com.example.diallog.data.repository;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.R;
import com.example.diallog.auth.AuthTokenProvider;
import com.example.diallog.data.model.TranscriptionResult;
import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.network.GoogleOperationResponse;
import com.example.diallog.data.network.GoogleSttApi;
import com.example.diallog.data.network.GoogleSttRequest;
import com.example.diallog.data.network.GoogleSttResponse;
import com.example.diallog.utils.MediaResolver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

public final class GoogleTranscriber implements Transcriber {
    public enum Mode { QUICK, FULL }

    public static final class AudioInput {
        @Nullable final Uri uri;
        @Nullable final byte[] inlineBytes;
        final int sampleRateHz;
        @Nullable final String mimeType;
        final boolean forceLinear16;

        private AudioInput(@Nullable Uri uri,
                           @Nullable byte[] inlineBytes,
                           int sampleRateHz,
                           @Nullable String mimeType,
                           boolean forceLinear16) {
            this.uri = uri;
            this.inlineBytes = inlineBytes;
            this.sampleRateHz = sampleRateHz;
            this.mimeType = mimeType;
            this.forceLinear16 = forceLinear16;
        }

        @NonNull
        public static AudioInput forUri(@NonNull Uri uri) {
            return new AudioInput(uri, null, 0, null, false);
        }

        @NonNull
        public static AudioInput forLinear16(@NonNull byte[] pcm, int sampleRateHz) {
            return new AudioInput(null, pcm, sampleRateHz, null, true);
        }

        boolean hasUri() {
            return uri != null;
        }

        boolean hasInline() {
            return inlineBytes != null;
        }
    }

    private static final long QUICK_CALL_TIMEOUT_MS = 10_000L;
    private static final long LONG_RUNNING_INITIAL_DELAY_MS = 1_000L;
    private static final long LONG_RUNNING_MAX_DELAY_MS = 10_000L;
    private static final int MAX_LONG_RUNNING_POLLS = 60;


    private final Context app;
    private final GoogleSttApi api;
    private final AuthTokenProvider tokenProvider;
    private String language = "en-US";

    public GoogleTranscriber(Context app, Retrofit retrofit, AuthTokenProvider tokenProvider) {
        this.app = app.getApplicationContext();
        this.api = retrofit.create(GoogleSttApi.class);
        this.tokenProvider = tokenProvider;
    }

    @Override
    public @NonNull TranscriptionResult transcribe(@NonNull Uri audioUri) {
        return transcribe(AudioInput.forUri(audioUri), language, Mode.FULL);
    }

    @Override
    public @NonNull TranscriptionResult transcribe(@NonNull Uri audioUri, @NonNull String languageCode) {
        return transcribe(AudioInput.forUri(audioUri), languageCode, Mode.FULL);
    }


    public @NonNull TranscriptionResult transcribe(@NonNull AudioInput input,
                                                   @NonNull String languageCode,
                                                   @NonNull Mode mode) {
        if (mode == Mode.QUICK) {
            return transcribeQuick(input, languageCode);
        }
        return transcribeFull(input, languageCode);
    }

    public @NonNull TranscriptionResult transcribe(@NonNull Uri audioUri,
                                                   @NonNull String languageCode,
                                                   @NonNull Mode mode) {
        return transcribe(AudioInput.forUri(audioUri), languageCode, mode);
    }

    public @NonNull TranscriptionResult transcribeQuick(@NonNull byte[] pcm16k,
                                                        int sampleRateHz,
                                                        @NonNull String languageCode) {
        return transcribe(AudioInput.forLinear16(pcm16k, sampleRateHz), languageCode, Mode.QUICK);
    }



    private TranscriptionResult transcribeQuick(@NonNull AudioInput input, @NonNull String languageCode) {
        if (!input.hasInline()) {
            throw new IllegalArgumentException("QUICK mode requires inline PCM content");
        }
        try {
            String effectiveLanguage = TextUtils.isEmpty(languageCode) ? language : languageCode;
            String base64 = Base64.encodeToString(input.inlineBytes, Base64.NO_WRAP);
            GoogleSttRequest request = buildRequest(base64, effectiveLanguage, input.mimeType, input.sampleRateHz, input.forceLinear16);


            Response<GoogleSttResponse> response = executeRecognize(request, QUICK_CALL_TIMEOUT_MS);
            if (response.code() == 401 || response.code() == 403) {
                tokenProvider.invalidate();
                response = executeRecognize(request, QUICK_CALL_TIMEOUT_MS);
            }

            if (!response.isSuccessful() || response.body() == null) {
                String errorMessage = extractError(response);

                throw new IOException("Google STT quick failed: HTTP " + response.code() + errorMessage);
            }

            List<TranscriptSegment> segments = mapResponse(response.body());
            return TranscriptionResult.interim(segments, null);
        } catch (IOException ioe) {
            throw new RuntimeException("Google STT I/O error: " + ioe.getMessage(), ioe);
        } catch (Exception e) {
            throw new RuntimeException("Google STT error: " + e.getMessage(), e);
        }
    }



    private TranscriptionResult transcribeFull(@NonNull AudioInput input, @NonNull String languageCode) {
        if (!input.hasUri()) {
            throw new IllegalArgumentException("FULL mode requires a URI input");
        }
        MediaResolver.ResolvedAudio resolved = null;
        try {
            MediaResolver resolver = new MediaResolver(app);
            resolved = resolver.resolveWithFallback(input.uri, app.getResources(), R.raw.sample1, "sample1.mp3");

            int sampleRateHz = GoogleSttAudioHelper.extractSampleRateHz(resolved.file);
            byte[] audioBytes = readFile(resolved.file);
            String base64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP);

            String effectiveLanguage = TextUtils.isEmpty(languageCode) ? language : languageCode;
            GoogleSttRequest request = buildRequest(base64, effectiveLanguage, resolved.mime, sampleRateHz, false);

            GoogleOperationResponse operation = startLongRunning(request);
            GoogleSttResponse body;
            if (operation.done && operation.response != null) {
                body = operation.response;
            } else {
                body = awaitOperation(operation.name);
            }

            List<TranscriptSegment> segments = mapResponse(body);
            return TranscriptionResult.finalResult(segments, null);
        } catch (IOException ioe) {
            throw new RuntimeException("Google STT I/O error: " + ioe.getMessage(), ioe);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Google STT interrupted", ie);
        } catch (Exception e) {
            throw new RuntimeException("Google STT error: " + e.getMessage(), e);
        } finally {
            if (resolved != null && resolved.tempCopy) {
                //noinspection ResultOfMethodCallIgnored
                resolved.file.delete();
            }
        }
    }


    private Response<GoogleSttResponse> executeRecognize(GoogleSttRequest request, long callTimeoutMs) throws Exception {
        String token = tokenProvider.getToken();
        Call<GoogleSttResponse> call = api.recognize("Bearer " + token, request);
        if (callTimeoutMs > 0L) {
            call.timeout().timeout(callTimeoutMs, TimeUnit.MILLISECONDS);
        }
        return call.execute();
    }

    private Response<GoogleOperationResponse> executeLongRunning(GoogleSttRequest request) throws Exception {
        String token = tokenProvider.getToken();

        Call<GoogleOperationResponse> call = api.longRunningRecognize("Bearer " + token, request);
        return call.execute();
    }

    private Response<GoogleOperationResponse> executeGetOperation(String name) throws Exception {
        String token = tokenProvider.getToken();
        Call<GoogleOperationResponse> call = api.getOperation("Bearer " + token, name);
        return call.execute();
    }

    private GoogleOperationResponse startLongRunning(GoogleSttRequest request) throws Exception {
        Response<GoogleOperationResponse> response = executeLongRunning(request);
        if (response.code() == 401 || response.code() == 403) {
            tokenProvider.invalidate();
            response = executeLongRunning(request);
        }
        if (!response.isSuccessful() || response.body() == null) {
            String errorMessage = extractError(response);
            throw new IOException("Google STT long running failed: HTTP " + response.code() + errorMessage);
        }
        GoogleOperationResponse body = response.body();
        if (body.error != null) {
            throw new IOException("Google STT long running error: " + body.error.message);
        }
        if (TextUtils.isEmpty(body.name) && !body.done) {
            throw new IOException("Google STT long running did not return operation name");
        }
        return body;
    }

    private GoogleSttResponse awaitOperation(@NonNull String name) throws Exception {
        int attempts = 0;
        long delayMs = LONG_RUNNING_INITIAL_DELAY_MS;
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while polling Google STT operation");
            }
            Response<GoogleOperationResponse> poll = executeGetOperation(name);
            if (poll.code() == 401 || poll.code() == 403) {
                tokenProvider.invalidate();
                poll = executeGetOperation(name);
            }
            if (!poll.isSuccessful() || poll.body() == null) {
                String errorMessage = extractError(poll);
                throw new IOException("Google STT operation poll failed: HTTP " + poll.code() + errorMessage);
            }
            GoogleOperationResponse body = poll.body();
            if (body.error != null) {
                throw new IOException("Google STT operation error: " + body.error.message);
            }
            if (body.done) {
                if (body.response != null) {
                    return body.response;
                }
                return new GoogleSttResponse();
            }
            attempts++;
            if (attempts >= MAX_LONG_RUNNING_POLLS) {
                throw new IOException("Google STT operation timed out");
            }
            try {
                Thread.sleep(Math.max(1L, delayMs));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            }
            delayMs = Math.min(delayMs * 2L, LONG_RUNNING_MAX_DELAY_MS);
        }
    }

    private GoogleSttRequest buildRequest(String base64Content,
                                          String languageCode,
                                          @Nullable String mimeType,
                                          int sampleRateHz,
                                          boolean forceLinear16) {
        GoogleSttRequest request = new GoogleSttRequest();
        GoogleSttRequest.Config config = new GoogleSttRequest.Config();
        config.languageCode = TextUtils.isEmpty(languageCode) ? language : languageCode;
        config.enableAutomaticPunctuation = true;
        config.enableWordTimeOffsets = true;
        config.maxAlternatives = 1;
        if (!forceLinear16) {
            config.model = "latest_long";
        }
        if (forceLinear16) {
            config.encoding = "LINEAR16";
            int effectiveRate = sampleRateHz > 0 ? sampleRateHz : 16_000;
            config.sampleRateHertz = effectiveRate;
        } else {
            GoogleSttAudioHelper.applyEncoding(config, mimeType, sampleRateHz);
        }
        request.config = config;

        GoogleSttRequest.Audio audio = new GoogleSttRequest.Audio();
        audio.content = base64Content;
        request.audio = audio;
        return request;
    }

    private static List<TranscriptSegment> mapResponse(@Nullable GoogleSttResponse body) {
        List<TranscriptSegment> segments = new ArrayList<>();
        if (body == null || body.results == null || body.results.isEmpty()) {
            return segments;
        }

        long lastEndMs = 0L;
        for (GoogleSttResponse.Result result : body.results) {
            if (result == null || result.alternatives == null || result.alternatives.isEmpty()) {
                continue;
            }
            long resultEndMs = parseDurationToMillis(result.resultEndTime);

            for (GoogleSttResponse.Alternative alternative : result.alternatives) {
                if (alternative == null) {
                    continue;
                }

                String transcript = alternative.transcript != null ? alternative.transcript.trim() : "";
                if (transcript.isEmpty()) {
                    continue;
                }

                long startMs = lastEndMs;
                long endMs = lastEndMs;
                boolean hasWords = alternative.words != null && !alternative.words.isEmpty();
                boolean foundTimestamps = false;

                if (hasWords) {
                    long firstStart = -1L;
                    long lastEnd = -1L;
                    long lastStart = -1L;

                    for (GoogleSttResponse.WordInfo wordInfo : alternative.words) {
                        if (wordInfo == null) {
                            continue;
                        }

                        long wordStart = parseDurationToMillis(wordInfo.startTime);
                        long wordEnd = parseDurationToMillis(wordInfo.endTime);

                        if (wordStart >= 0 && firstStart < 0) {
                            firstStart = wordStart;
                        }
                        if (wordEnd >= 0) {
                            lastEnd = wordEnd;
                        }
                        if (wordStart >= 0) {
                            lastStart = wordStart;
                        }
                        if (wordStart >= 0 || wordEnd >= 0) {
                            foundTimestamps = true;
                        }
                    }
                    if (firstStart >= 0) {
                        startMs = firstStart;
                    }

                    long candidateEnd = lastEnd >= 0 ? lastEnd : lastStart;
                    if (candidateEnd >= 0) {
                        endMs = candidateEnd;
                    }
                }

                if (!hasWords || !foundTimestamps) {
                    if (resultEndMs >= 0) {
                        endMs = Math.max(resultEndMs, startMs);
                    }
                }

                if (endMs < startMs) {
                    endMs = startMs;
                }

                segments.add(new TranscriptSegment(transcript, startMs, endMs, 1.0));
                if (endMs > lastEndMs) {
                    lastEndMs = endMs;
                }
            }
            if (resultEndMs > lastEndMs) {
                lastEndMs = resultEndMs;
            }
        }
        return segments;
    }



    private static long parseDurationToMillis(@Nullable String duration) {
        if (TextUtils.isEmpty(duration)) {
            return -1L;
        }
        String trimmed = duration.trim();
        if (trimmed.endsWith("s") || trimmed.endsWith("S")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return -1L;
        }

        try {
            BigDecimal seconds = new BigDecimal(trimmed);
            BigDecimal millis = seconds.multiply(BigDecimal.valueOf(1000L));
            return millis.setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return -1L;
        }
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

    private static String extractError(Response<?> response) {
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