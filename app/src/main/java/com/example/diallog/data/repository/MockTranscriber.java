package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.TranscriptionResult;
import com.example.diallog.data.model.TranscriptSegment;

import java.util.ArrayList;
import java.util.List;


public final class MockTranscriber implements Transcriber {
    @Override
    public @NonNull TranscriptionResult transcribe(@NonNull Uri uri) {
        List<TranscriptSegment> ret = new ArrayList<>();
        ret.add(new TranscriptSegment("안녕하세요. 통화 테스트입니다.", 0, 3000));
        ret.add(new TranscriptSegment("내일 오후 두 시에 회의 가능하실까요?", 3000, 9000));
        ret.add(new TranscriptSegment("장소는 본사 3층 회의실입니다.", 9000, 14000));
        return TranscriptionResult.finalResult(ret, null);
    }
}
