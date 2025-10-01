package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.TranscriberResult;
import com.example.diallog.data.model.Transcript;

import java.util.ArrayList;
import java.util.List;


public final class MockTranscriber implements Transcriber {
    @Override
    public @NonNull TranscriberResult transcribe(@NonNull Uri uri) {
        List<Transcript> ret = new ArrayList<>();
        ret.add(new Transcript("안녕하세요. 통화 테스트입니다.", 0, 3000, 1.0F, null));
        ret.add(new Transcript("내일 오후 두 시에 회의 가능하실까요?", 3000, 9000, 1.0F, null));
        ret.add(new Transcript("장소는 본사 3층 회의실입니다.", 9000, 14000, 1.0F, null));
        return TranscriberResult.success(ret, null);
    }
}
