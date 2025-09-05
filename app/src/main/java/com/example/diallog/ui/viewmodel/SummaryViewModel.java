package com.example.diallog.ui.viewmodel;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.diallog.data.model.TranscriptSegment;
import com.example.diallog.data.repository.MockTranscriber;
import com.example.diallog.data.repository.Transcriber;

import java.util.List;

public final class SummaryViewModel extends ViewModel {
    private final MutableLiveData<List<TranscriptSegment>> segments = new MutableLiveData<>();
    public LiveData<List<TranscriptSegment>> getSegments() { return segments; }

    public void loadMock(String audioPath) {
        Transcriber t = new MockTranscriber();
        List<TranscriptSegment> list = t.transcribe(audioPath);
        segments.postValue(list);
    }

}
