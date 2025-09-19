package com.example.diallog.utils;

import android.content.Context;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class AudioSnipper {
    private static final String TAG = "AudioSnipper";
    private static final int TARGET_SAMPLE_RATE = 16_000;
    private static final int DEQUEUE_TIMEOUT_US = 10_000;

    public static final class SnippedAudio {
        public final byte[] data;
        public final int sampleRateHz;

        SnippedAudio(byte[] data, int sampleRateHz) {
            this.data = data;
            this.sampleRateHz = sampleRateHz;
        }

        public boolean isEmpty() {
            return data == null || data.length == 0;
        }
    }

    private final Context app;
    private final Resources resources;
    private final int fallbackRawId;
    private final String fallbackName;

    public AudioSnipper(Context context, int fallbackRawId, @NonNull String fallbackName) {
        this.app = context.getApplicationContext();
        this.resources = context.getResources();
        this.fallbackRawId = fallbackRawId;
        this.fallbackName = fallbackName;
    }

    @NonNull
    public SnippedAudio snipHead(@NonNull Uri audioUri, int maxSeconds) {
        MediaResolver.ResolvedAudio resolved = null;
        try {
            MediaResolver resolver = new MediaResolver(app);
            resolved = resolver.resolveWithFallback(audioUri, resources, fallbackRawId, fallbackName);
            SnippedAudio audio = decodeHead(resolved.file, maxSeconds);
            Log.i(TAG, "snip.done bytes=" + audio.data.length + " sampleRate=" + audio.sampleRateHz);
            return audio;
        } catch (Exception e) {
            Log.w(TAG, "snipHead: failed", e);
            return new SnippedAudio(new byte[0], TARGET_SAMPLE_RATE);
        } finally {
            if (resolved != null && resolved.tempCopy) {
                //noinspection ResultOfMethodCallIgnored
                resolved.file.delete();
            }
        }
    }

    @NonNull
    private SnippedAudio decodeHead(@NonNull File file, int maxSeconds) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(file.getAbsolutePath());
        int trackIndex = selectAudioTrack(extractor);
        if (trackIndex < 0) {
            extractor.release();
            return new SnippedAudio(new byte[0], TARGET_SAMPLE_RATE);
        }
        extractor.selectTrack(trackIndex);
        MediaFormat format = extractor.getTrackFormat(trackIndex);
        String mime = format.getString(MediaFormat.KEY_MIME);
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        int sourceSampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                : TARGET_SAMPLE_RATE;
        int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                : 1;

        ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long maxDurationUs = maxSeconds <= 0 ? Long.MAX_VALUE : maxSeconds * 1_000_000L;
        boolean inputDone = false;
        boolean outputDone = false;

        try {
            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                        if (inputBuffer == null) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, 0);
                        } else {
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            long sampleTime = extractor.getSampleTime();
                            if (sampleSize < 0 || sampleTime > maxDurationUs) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, extractor.getSampleFlags());
                                extractor.advance();
                            }
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                    if (info.size > 0 && outputBuffer != null) {
                        byte[] chunk = new byte[info.size];
                        outputBuffer.get(chunk);
                        outputBuffer.clear();
                        rawOutput.write(chunk);
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                    codec.releaseOutputBuffer(outputIndex, false);
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = codec.getOutputFormat();
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sourceSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (inputDone) {
                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    }
                }
            }
        } finally {
            codec.stop();
            codec.release();
            extractor.release();
        }

        byte[] decoded = rawOutput.toByteArray();
        if (decoded.length == 0) {
            return new SnippedAudio(decoded, TARGET_SAMPLE_RATE);
        }
        short[] samples = toShortArray(decoded);
        short[] mono = downmixToMono(samples, channelCount);
        short[] resampled = resample(mono, sourceSampleRate, TARGET_SAMPLE_RATE);
        byte[] pcm = toByteArray(resampled);
        return new SnippedAudio(pcm, TARGET_SAMPLE_RATE);
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private static short[] toShortArray(byte[] data) {
        int length = data.length / 2;
        short[] out = new short[length];
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out);
        return out;
    }

    private static short[] downmixToMono(short[] samples, int channelCount) {
        if (channelCount <= 1) {
            return samples;
        }
        int frames = samples.length / channelCount;
        short[] mono = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channelCount; c++) {
                sum += samples[i * channelCount + c];
            }
            mono[i] = (short) (sum / channelCount);
        }
        return mono;
    }

    private static short[] resample(short[] samples, int sourceRate, int targetRate) {
        if (samples.length == 0) {
            return samples;
        }
        if (sourceRate <= 0) {
            sourceRate = targetRate;
        }
        if (targetRate <= 0) {
            targetRate = sourceRate;
        }
        if (sourceRate == targetRate) {
            return samples;
        }
        double rateRatio = (double) targetRate / (double) sourceRate;
        int outputLength = Math.max(1, (int) Math.round(samples.length * rateRatio));
        short[] output = new short[outputLength];
        for (int i = 0; i < outputLength; i++) {
            double srcIndex = i / rateRatio;
            int index = (int) Math.floor(srcIndex);
            double frac = srcIndex - index;
            short s1 = samples[Math.min(index, samples.length - 1)];
            short s2 = samples[Math.min(index + 1, samples.length - 1)];
            output[i] = (short) ((1 - frac) * s1 + frac * s2);
        }
        return output;
    }

    private static byte[] toByteArray(short[] samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asShortBuffer().put(samples);
        return buffer.array();
    }
}