package com.example.diallog.data.repository;

import android.content.Context;
import android.net.Uri;
import java.util.*;

import com.example.diallog.data.model.TranscriptSegment;


public final class MockTranscriber implements Transcriber {
    @Override public List<TranscriptSegment> transcribe(String audioPath) {
        List<TranscriptSegment> ret = new ArrayList<>();
        ret.add(new TranscriptSegment("안녕하세요", 0, 2000));
        ret.add(new TranscriptSegment("테스트 음성입니다", 2000, 5000));
        return ret;
    }
}
