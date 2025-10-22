package com.example.diallog.data.network;


import androidx.annotation.NonNull;

import com.example.diallog.data.model.Transcript;

import java.io.IOException;
import java.util.List;

public interface SummaryGenerator {
    @NonNull
    String summarize(@NonNull List<Transcript> segments) throws IOException;
}
