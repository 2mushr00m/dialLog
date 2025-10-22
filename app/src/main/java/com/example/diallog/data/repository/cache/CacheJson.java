package com.example.diallog.data.repository.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.data.model.Transcript;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class CacheJson {

    static final class Meta {
        final long durationMs;
        @Nullable final String allText;
        @Nullable final String summary;
        Meta(long durationMs, @Nullable String allText, @Nullable String summary) {
            this.durationMs = durationMs; this.allText = allText; this.summary = summary;
        }
    }

    @NonNull static JSONObject toJson(@NonNull List<Transcript> segs,
                                      @Nullable String summary,
                                      @Nullable String allText) throws JSONException {
        JSONObject root = new JSONObject();
        if (allText != null) root.put("allText", allText);
        if (summary != null) root.put("summary", summary);

        JSONArray arr = new JSONArray();
        for (Transcript t : segs) {
            if (t == null) continue;
            JSONObject o = new JSONObject();

            o.put("text", t.text != null ? t.text : "");
            o.put("startMs", t.startMs);
            o.put("endMs", t.endMs);

            if (t.confidence != null)   o.put("confidence", t.confidence);
            if (t.speakerLabel != null) o.put("speakerLabel", t.speakerLabel);
            arr.put(o);
        }
        root.put("segments", arr);
        return root;
    }

    @NonNull static List<Transcript> parseSegments(@NonNull JSONObject root) {
        List<Transcript> out = new ArrayList<>();
        JSONArray arr = root.optJSONArray("segments");
        if (arr == null) return out;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Transcript t = new Transcript(
                    o.optString("text", ""),
                    o.optLong("startMs", Long.MIN_VALUE),
                    o.optLong("endMs",   Long.MIN_VALUE),
                    o.optDouble("confidence", 1),
                    o.optString("speakerLabel", null)
                    );
            out.add(t);
        }
        return out;
    }

    @NonNull static Meta parseMeta(@NonNull JSONObject root) {
        long dur = root.optLong("durationMs", 0L);

        String allText = root.optString("allText", null);
        if (allText != null && allText.isEmpty()) allText = null;

        String summary = root.optString("summary", null);
        if (summary != null && summary.isEmpty()) summary = null;

        return new Meta(dur, allText, summary);
    }

    private CacheJson() {}
}