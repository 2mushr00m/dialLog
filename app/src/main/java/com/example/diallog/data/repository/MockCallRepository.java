package com.example.diallog.data.repository;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.R;
import com.example.diallog.data.model.CallRecord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class MockCallRepository implements CallRepository {
    private final List<CallRecord> fake = new ArrayList<>();

    public MockCallRepository(@NonNull Context app) {
        // 캐시에 샘플 파일 복사 후 그 절대경로를 path로 사용
        File a = copyRawToCache(app, R.raw.sample1, "mock_a.mp3");
        File b = copyRawToCache(app, R.raw.sample1, "mock_b.mp3");
        long now = System.currentTimeMillis();
        fake.add(new CallRecord(Uri.fromFile(a), "통화1", 120_000, now));
        fake.add(new CallRecord(Uri.fromFile(b), "통화2",  60_000, now - 3_600_000));
    }

    @Override
    public List<CallRecord> getRecent(int offset, int limit) {
        int end = Math.min(offset + limit, fake.size());
        if (offset >= fake.size()) return new ArrayList<>();
        return new ArrayList<>(fake.subList(offset, end));
    }

    @Override public @Nullable CallRecord getByUri(@NonNull Uri uri) {
        for (CallRecord r : fake) if (r.uri.equals(uri)) return r;
        return null;
    }

    private static File copyRawToCache(Context app, int resId, String name) {
        try {
            File dst = new File(app.getCacheDir(), name);
            try (InputStream in = app.getResources().openRawResource(resId);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            return dst;
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}