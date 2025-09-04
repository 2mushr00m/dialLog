package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.Nullable;

public final class FindCallRecordFolder {
    private FindCallRecordFolder() {};

    public static class CallRecordPath {
        public final String relativePath;
        public final Uri sampleUri;
        public CallRecordPath(String p, Uri u){ this.relativePath=p; this.sampleUri=u; }
    }

    /*
    통화녹음 폴더 선정
    - 최근 180일 이내 음성 파일이 생성되었고, 파일명/경로에 call/phone/record/rec/통화/녹음 이 포함된 경로를 스캔.
    - 조건에 최다로 부합하는 폴더를 CallRecordPath.relative_path로 자동 선정.
     */
    @Nullable
    public static Result findLikelyCallRecordDir(Context ctx) {
        ContentResolver cr = ctx.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED
        };

        long nowSec = System.currentTimeMillis()/1000L;
        long days180 = 180L * 24L * 3600L;

        // 키워드: 영문/국문 모두
        String like = "%call%";
        String like2 = "%record%";
        String like3 = "%rec%";
        String likeKr = "%통화%";
        String likeKr2 = "%녹음%";

        String selection =
                "(" + MediaStore.Audio.Media.DATE_ADDED + " >= ?) AND " +
                        "(" + MediaStore.Audio.Media.DURATION + " BETWEEN ? AND ?) AND (" +
                        MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ? OR " +
                        MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ? OR " +
                        MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ? OR " +
                        MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ? OR " +
                        MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ? OR " +
                        MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ? OR " +
                        MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ? OR " +
                        MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ? OR " +
                        MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ? OR " +
                        MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ? )";

        String[] args = new String[] {
                String.valueOf(nowSec - days180),
                String.valueOf(2_000),          // 2초
                String.valueOf(3L*60L*60L*1000L), // 3시간(ms)
                like, like2, like3, likeKr, likeKr2,
                like, like2, like3, likeKr, likeKr2
        };

        // 최근 것부터
        String order = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        Map<String, CountAndUri> bucket = new HashMap<>();
        try (Cursor c = cr.query(uri, projection, selection, args, order)) {
            if (c == null) return null;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String name = safe(c.getString(1));
                String relPath = safe(c.getString(2));
                long dur = c.getLong(3);

                // 방어: 상대경로 없으면 스킵
                if (relPath.isEmpty()) continue;

                // 추가 휴리스틱: 폴더명/파일명에 ‘call/통화/record/rec’ 포함 다시 확인(대소문자 무시)
                String mix = (relPath + "/" + name).toLowerCase(Locale.ROOT);
                if (!(mix.contains("call") || mix.contains("record") || mix.contains("rec")
                        || mix.contains("통화") || mix.contains("녹음"))) {
                    continue;
                }

                Uri itemUri = Uri.withAppendedPath(uri, String.valueOf(id));
                bucket.compute(relPath, (k, v) -> {
                    if (v == null) return new CountAndUri(1, itemUri);
                    v.count++; return v;
                });
            }
        }

        // 최다 발생 폴더 선택
        String bestPath = null;
        Uri sample = null;
        int best = 0;
        for (Map.Entry<String, CountAndUri> e : bucket.entrySet()) {
            if (e.getValue().count > best) {
                best = e.getValue().count;
                bestPath = e.getKey();
                sample = e.getValue().anyUri;
            }
        }

        if (bestPath == null) return null;
        return new Result(bestPath, sample);
    }

    private static String safe(String s){ return s==null ? "" : s; }

    private static class CountAndUri {
        int count;
        Uri anyUri;
        CountAndUri(int c, Uri u){ count=c; anyUri=u; }
    }

}
