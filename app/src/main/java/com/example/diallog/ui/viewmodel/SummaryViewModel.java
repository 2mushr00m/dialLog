package com.example.diallog.ui.viewmodel;


import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.diallog.data.model.TranscriberResult;
import com.example.diallog.data.model.Transcript;
import com.example.diallog.data.model.TranscriptSection;
import com.example.diallog.data.repository.Transcriber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;


public final class SummaryViewModel extends ViewModel {
    private final Transcriber transcriber;
    private final MutableLiveData<List<TranscriptSection>> sections = new MutableLiveData<>();
    private final MutableLiveData<List<Transcript>> segments = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger jobCounter = new AtomicInteger();
    private Future<?> running;


    public LiveData<List<TranscriptSection>> sections(){ return sections; }
    public LiveData<List<Transcript>> segments(){ return segments; }
    public LiveData<Boolean> loading(){ return loading; }
    public LiveData<String> error(){ return error; }


    public SummaryViewModel(@NonNull Transcriber transcriber) {
        this.transcriber = transcriber;
    }

    @MainThread
    public void transcribe(@NonNull Uri audioUri){
        Future<?> prev = running;
        if (prev != null) prev.cancel(true);

        int jobId = jobCounter.incrementAndGet();
        loading.setValue(true);
        error.setValue(null);

        running = io.submit(() -> {
            final int myJob = jobId;
            try {
                TranscriberResult raw = transcriber.transcribe(audioUri);

                List<Transcript> sorted = new ArrayList<>(raw.segments);
                sorted.sort(Comparator.comparingLong(t -> t.startMs));

                List<TranscriptSection> grouped = groupByConsecutiveSpeaker(sorted);

                if (jobCounter.get() == myJob && !Thread.currentThread().isInterrupted()) {
                    sections.postValue(Collections.unmodifiableList(grouped));
                }
            } catch (Throwable t) {
                if (jobCounter.get() == myJob) {
                    error.postValue(t.getMessage() != null ? t.getMessage() : "Transcribe failed");
                }
            } finally {
                if (jobCounter.get() == myJob) loading.postValue(false);
            }
        });
    }

    @MainThread
    public void submitRawTranscripts(@NonNull List<Transcript> transcripts) {
        List<Transcript> sorted = sortByStartAscending(transcripts);
        List<TranscriptSection> grouped = groupByConsecutiveSpeaker(sorted);
        sections.setValue(Collections.unmodifiableList(grouped));
        loading.setValue(false);
        error.setValue(null);
    }

    @NonNull
    private static List<Transcript> sortByStartAscending(@NonNull List<Transcript> src) {
        List<Transcript> out = new ArrayList<>(src);
        out.sort(Comparator.comparingLong(t -> t.startMs));
        return out;
    }

    @NonNull
    private static List<TranscriptSection> groupByConsecutiveSpeaker(@NonNull List<Transcript> sorted) {
        List<TranscriptSection> out = new ArrayList<>();
        if (sorted.isEmpty()) return out;

        List<Transcript> bucket = new ArrayList<>();
        int currentSpeaker = speakerOf(sorted.get(0));

        for (Transcript t : sorted) {
            int sp = speakerOf(t);
            if (sp == currentSpeaker) {
                bucket.add(t);
            } else {
                out.add(makeSection(bucket, currentSpeaker));
                bucket = new ArrayList<>();
                bucket.add(t);
                currentSpeaker = sp;
            }
        }
        // 마지막 버킷 플러시
        out.add(makeSection(bucket, currentSpeaker));
        return out;
    }

    private static int speakerOf(@NonNull Transcript t) {
        // 프로젝트 규칙에 맞춰 speakerId를 결정하세요.
        // 현재 Transcript에는 speakerId 필드가 없으므로, speakerLabel을 해시로 매핑하거나,
        // STT 단계에서 Transcript에 일관된 speakerId를 채워 넣으셨다면 그 값을 사용하십시오.
        // 기본 구현: speakerLabel이 같으면 동일 화자, null/빈 문자열은 -1로 처리.
        if (t.speakerLabel == null || t.speakerLabel.isEmpty()) return -1;
        return t.speakerLabel.hashCode();
    }

    @NonNull
    private static TranscriptSection makeSection(@NonNull List<Transcript> items, int speakerId) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            String s = items.get(i).text != null ? items.get(i).text : "";
            if (!s.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(s);
            }
        }
        return new TranscriptSection(sb.toString(), speakerId, Collections.unmodifiableList(items));
    }

}
