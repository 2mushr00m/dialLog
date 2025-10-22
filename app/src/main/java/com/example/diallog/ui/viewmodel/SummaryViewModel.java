package com.example.diallog.ui.viewmodel;


import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.diallog.data.model.TranscriberResult;
import com.example.diallog.data.model.Transcript;
import com.example.diallog.data.repository.Transcriber;
import com.example.diallog.data.repository.cache.TranscriptCache;
import com.example.diallog.ui.adapter.TranscriptAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;


public final class SummaryViewModel extends ViewModel {
    private final Transcriber transcriber;
    private final TranscriptCache tcache;

    private final MutableLiveData<List<Transcript>> segments = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger jobCounter = new AtomicInteger();
    private Future<?> running;


    public SummaryViewModel(@NonNull Transcriber transcriber, @NonNull TranscriptCache tcache) {
        this.transcriber = transcriber;
        this.tcache = tcache;
    }

    public LiveData<List<Transcript>> segments() { return segments; }
    public LiveData<Boolean> loading()           { return loading; }
    public LiveData<String> error()              { return error; }
    public TranscriptAdapter.ChipProvider chipProvider() { return chipProvider; }


    /** 캐시가 있으면 즉시 로드. 없으면 STT 실행. */
    @MainThread
    public void load(@NonNull Uri audioUri) {
        cancelRunning();
        int jobId = jobCounter.incrementAndGet();
        loading.setValue(true);
        error.setValue(null);

        running = io.submit(() -> {
            final int myJob = jobId;
            try {
                // 1) 캐시 우선
                List<Transcript> cached = safeSort(tcache.get(audioUri));
                if (!cached.isEmpty()) {
                    if (jobCounter.get() == myJob && !Thread.currentThread().isInterrupted()) {
                        segments.postValue(Collections.unmodifiableList(cached));
                        loading.postValue(false);
                    }
                    return;
                }

                // 2) 캐시 없으면 STT 수행
                TranscriberResult res = transcriber.transcribe(audioUri);

                // 3) 저장은 Transcriber/CachedTranscriber 쪽에 위임. 다시 캐시에서 읽기.
                List<Transcript> fresh = safeSort(tcache.get(audioUri));
                List<Transcript> out = fresh.isEmpty() ? safeSort(res.segments) : fresh;

                if (jobCounter.get() == myJob && !Thread.currentThread().isInterrupted()) {
                    segments.postValue(Collections.unmodifiableList(out));
                }
            } catch (Throwable t) {
                if (jobCounter.get() == myJob) {
                    error.postValue(t.getMessage() != null ? t.getMessage() : "Transcribe failed");
                }
            } finally {
                if (jobCounter.get() == jobId) loading.postValue(false);
            }
        });
    }

    @MainThread
    public void submitTranscripts(@NonNull List<Transcript> list) {
        cancelRunning();
        error.setValue(null);
        loading.setValue(false);
        segments.setValue(Collections.unmodifiableList(safeSort(list)));
    }

    @MainThread
    public void cancelRunning() {
        Future<?> prev = running;
        if (prev != null) prev.cancel(true);
    }

    @Override protected void onCleared() {
        cancelRunning();
        io.shutdownNow();
        super.onCleared();
    }

    @NonNull
    private static List<Transcript> safeSort(List<Transcript> in) {
        if (in == null || in.isEmpty()) return Collections.emptyList();
        List<Transcript> out = new ArrayList<>(in);
        out.sort(Comparator.comparingLong(t -> t.startMs));
        return out;
    }


    private volatile float confidenceThreshold = 0.70f;
    private volatile String lowConfLabel = "신뢰도 낮음";

    private final TranscriptAdapter.ChipProvider chipProvider = t -> {
        List<TranscriptAdapter.ChipSpec> out = new ArrayList<>();
        Float c = confidenceOf(t);
        if (c != null && c < confidenceThreshold) {
            int pct = Math.max(0, Math.min(100, Math.round(c * 100f)));
            out.add(new TranscriptAdapter.ChipSpec(
                    TranscriptAdapter.CHIP_LOW_CONFIDENCE,
                    lowConfLabel + " (" + pct + "%)"
            ));
        }
        return out;
    };
    @Nullable
    private static Float confidenceOf(@NonNull Transcript t){
        try {
            Field f = t.getClass().getField("confidence");
            Object v = f.get(t);
            if (v instanceof Number) return ((Number) v).floatValue();
        } catch (Throwable ignore) {}
        try {
            Method m = t.getClass().getMethod("getConfidence");
            Object v = m.invoke(t);
            if (v instanceof Number) return ((Number) v).floatValue();
        } catch (Throwable ignore) {}
        return null;
    }
}
