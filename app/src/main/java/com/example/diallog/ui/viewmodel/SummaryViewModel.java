package com.example.diallog.ui.viewmodel;


import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.model.TranscriptionResult;
import com.example.diallog.data.repository.MockTranscriber;
import com.example.diallog.data.repository.Transcriber;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;


public final class SummaryViewModel extends ViewModel {
    private final Transcriber transcriber;
    private final MutableLiveData<List<TranscriptSegment>> segments = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger jobCounter = new AtomicInteger();
    private final Object jobLock = new Object();
    private Future<?> runningJob;

    public SummaryViewModel(Transcriber transcriber) {
        this.transcriber = transcriber;
    }

    public LiveData<List<TranscriptSegment>> segments(){ return segments; }
    public LiveData<Boolean> loading(){ return loading; }
    public LiveData<String> error(){ return error; }


    public void transcribe(@NonNull Uri audioUri){
        int jobId = jobCounter.incrementAndGet();
        Future<?> previous;
        synchronized (jobLock) {
            previous = runningJob;
            runningJob = null;
        }
        if (previous != null) {
            previous.cancel(true);
        }
        loading.postValue(true);
        error.postValue(null);
        Future<?> future = io.submit(() -> {
            try {
                TranscriptionResult result = transcriber.transcribe(audioUri);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (!result.isFinal) {
                    return;
                }
                if (jobCounter.get() != jobId) {
                    return;
                }
                segments.postValue(result.segments);
            } catch (Exception e){
                if (jobCounter.get() != jobId) {
                    return;
                }
                error.postValue(e.getMessage());
            } finally {
                if (jobCounter.get() == jobId) {
                    loading.postValue(false);
                }
            }
        });
        synchronized (jobLock) {
            runningJob = future;
        }
    }


    @Override protected void onCleared(){
        synchronized (jobLock) {
            if (runningJob != null) {
                runningJob.cancel(true);
                runningJob = null;
            }
        }
        io.shutdownNow();
    }

    public void loadMock(@NonNull Uri audioUri) {
        Transcriber t = new MockTranscriber();
        TranscriptionResult result = t.transcribe(audioUri);
        segments.postValue(result.segments);
    }

}
