package com.example.diallog.data.repository;

import java.util.*;
import com.example.diallog.data.model.TranscriptSegment;


public interface Transcriber {
    List<TranscriptSegment> transcribe(String audioPath);
}
