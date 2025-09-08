package com.example.diallog.data.network;

import java.util.List;

public final class SttResponse {
    public List<Item> segments; // TODO: 실제 응답 구조에 맞게 필드 명시
    public static final class Item {
        public String text;
        public long startMs;
        public long endMs;
    }


}
