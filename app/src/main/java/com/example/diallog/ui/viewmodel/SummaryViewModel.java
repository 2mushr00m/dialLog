package com.example.diallog.ui.viewmodel;


import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.repository.MockTranscriber;
import com.example.diallog.data.repository.Transcriber;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SummaryViewModel extends ViewModel {
    private final Transcriber transcriber;
    private final MutableLiveData<List<TranscriptSegment>> segments = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final ExecutorService io = Executors.newSingleThreadExecutor();


    public SummaryViewModel(Transcriber transcriber) {
        this.transcriber = transcriber;
    }

    public LiveData<List<TranscriptSegment>> segments(){ return segments; }
    public LiveData<Boolean> loading(){ return loading; }
    public LiveData<String> error(){ return error; }


    public void transcribe(@NonNull String audioPath){
        if (Boolean.TRUE.equals(loading.getValue())) return;
        loading.postValue(true);
        error.postValue(null);
        io.submit(() -> {
            try {
                List<TranscriptSegment> list = transcriber.transcribe(audioPath);
                segments.postValue(list);
            } catch (Exception e){
                error.postValue(e.getMessage());
            } finally {
                loading.postValue(false);
            }
        });
    }

    @Override protected void onCleared(){ io.shutdownNow(); }

    public void loadMock(String audioPath) {
        Transcriber t = new MockTranscriber();
        List<TranscriptSegment> list = t.transcribe(audioPath);
        segments.postValue(list);
    }

}
