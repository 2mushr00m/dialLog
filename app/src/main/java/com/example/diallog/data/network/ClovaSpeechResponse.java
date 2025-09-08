package com.example.diallog.data.network;

import java.util.List;

public class ClovaSpeechResponse {
    public String text;
    public List<Seg> segments;
    public static final class Seg { public String text; public long startMs; public long endMs; }

}
