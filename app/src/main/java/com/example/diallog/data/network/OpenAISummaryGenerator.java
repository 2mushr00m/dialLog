package com.example.diallog.data.network;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.diallog.BuildConfig;
import com.example.diallog.data.model.Transcript;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import okhttp3.*;

public final class OpenAISummaryGenerator implements SummaryGenerator {
    private static final String TAG = "Summary";

    private static final String MODEL = "gpt-4o-mini";
    private static final int MAX_SOURCE_CHARS = 3000;
    private static final int MAX_OUTPUT_CHARS = 30;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .retryOnConnectionFailure(true).build();

    @NonNull @Override
    public String summarize(@NonNull List<Transcript> segments) throws IOException {
        long t0 = System.nanoTime();
        String merged = merge(segments, MAX_SOURCE_CHARS);
        if (TextUtils.isEmpty(merged)) {
            Log.w(TAG, "요약요청중단: 이유=empty_transcript");
            throw new IOException("empty transcript");
        }

        // 입력 텍스트 상태 로그
        Log.i(TAG, "요약시작: model=" + MODEL
                + " base=" + safeBase(BuildConfig.CHAT_GPT_BASE)
                + " srcLen=" + merged.length()
                + " maxOutTok=64");

        String system = "항상 한 문장만, 핵심 의도/행동을 매우 간결하게 요약하세요. 따옴표·불필요한 마침표 금지.";
        String user = "다음 통화 전사로 '리스트용 한 줄 요약'을 만드세요.\n"
                + "- 형식: 한 문장, 따옴표/불필요한 마침표 금지\n"
                + "- 예: 일요일 12시 송편 한 말 예약 / 오늘자 업무 일정 정리 요청\n"
                + "- 최대 " + MAX_OUTPUT_CHARS + "자\n\n[전사]\n" + merged;

        try {
            JSONObject body = new JSONObject();
            JSONArray msgs = new JSONArray()
                    .put(new JSONObject().put("role","system").put("content", system))
                    .put(new JSONObject().put("role","user").put("content", user));
            body.put("model", MODEL);
            body.put("messages", msgs);
            body.put("temperature", 0.2);
            body.put("max_tokens", 64);

            // 요청 준비 로그(민감정보 마스킹)
            Log.d(TAG, "요청준비: url=" + BuildConfig.CHAT_GPT_BASE
                    + " auth=" + maskKey(BuildConfig.CHAT_GPT_API_KEY)
                    + " bodySize=" + body.toString().length());

            Request req = new Request.Builder()
                    .url(BuildConfig.CHAT_GPT_BASE)
                    .addHeader("Authorization", "Bearer " + BuildConfig.CHAT_GPT_API_KEY)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .post(RequestBody.create(body.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build();

            long t1 = System.nanoTime();
            try (Response rsp = http.newCall(req).execute()) {
                long t2 = System.nanoTime();
                String reqId = rsp.header("x-request-id");
                int code = rsp.code();
                Log.i(TAG, "응답수신: http=" + code
                        + (reqId != null ? " reqId=" + reqId : "")
                        + " latencyMs=" + ms(t0, t2)
                        + " netMs=" + ms(t1, t2));

                String raw = rsp.body() != null ? rsp.body().string() : "";
                if (!rsp.isSuccessful()) {
                    String err = raw;
                    String cause = classifyError(err, code);
                    if ("quota".equals(cause)) {
                        Log.w(TAG, "요약불가: 원인=quota -> 폴백 권장");
                        return ""; // 예외 대신 빈 문자열 반환
                    }
                    throw new IOException("openai: " + err);
                }

                // 성공: 본문 길이/첫 80자만 로깅
                Log.d(TAG, "응답본문: size=" + raw.length() + " head=" + head(raw, 80));

                String oneLine = extract(raw);
                oneLine = postProcess(oneLine, MAX_OUTPUT_CHARS);
                if (TextUtils.isEmpty(oneLine)) {
                    Log.w(TAG, "후처리실패: 이유=empty_summary");
                    throw new IOException("empty summary");
                }
                Log.i(TAG, "요약완료: text=\"" + oneLine + "\" len=" + oneLine.length());
                return oneLine;
            }
        } catch (Exception e) {
            // 최종 예외 로그(스택 포함)
            Log.w(TAG, "요약예외: msg=" + e.getMessage(), e);
            throw new IOException(e);
        }
    }

    // ===== Helpers =====

    private static String merge(List<Transcript> segs, int max) {
        StringBuilder sb = new StringBuilder(Math.min(max, 4096));
        for (Transcript t : segs) {
            if (t == null || TextUtils.isEmpty(t.text)) continue;
            if (sb.length() + t.text.length() + 1 > max) {
                sb.append('\n').append("…"); // 절단 표시
                break;
            }
            if (sb.length() > 0) sb.append('\n');
            sb.append(t.text);
        }
        if (sb.length() > 0 && sb.charAt(sb.length()-1) == '\n') {
            sb.setLength(sb.length()-1);
        }
        if (sb.length() >= max) {
            Log.d(TAG, "입력절단: srcLen=" + sb.length() + " max=" + max);
        }
        return sb.toString();
    }

    private static String extract(String raw) throws Exception {
        JSONObject j = new JSONObject(raw);
        JSONArray choices = j.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";
        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
        return msg != null ? msg.optString("content", "").trim() : "";
    }

    private static String postProcess(String s, int limit) {
        if (s == null) return "";
        s = s.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("“") && s.endsWith("”")))
            s = s.substring(1, s.length()-1).trim();
        while (s.endsWith(".") || s.endsWith("…") || s.endsWith("\"") || s.endsWith("”"))
            s = s.substring(0, s.length()-1).trim();
        if (s.codePointCount(0, s.length()) > limit) {
            int idx = s.offsetByCodePoints(0, limit);
            s = s.substring(0, idx).trim();
        }
        return s;
    }

    // ===== Logging utilities =====

    private static String maskKey(String key) {
        if (key == null) return "null";
        int n = key.length();
        if (n <= 8) return "****";
        return key.substring(0, 6) + "****" + key.substring(n - 2);
    }

    private static String safeBase(String base) {
        if (base == null) return "null";
        // 경로 전체는 보이되, 프로토콜만 축약할 수도 있음
        return base;
    }

    private static String briefJson(String s, int max) {
        if (s == null) return "null";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…(" + s.length() + ")";
    }

    private static String head(String s, int n) {
        if (s == null) return "null";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static long ms(long t0, long t1) { return (t1 - t0) / 1_000_000L; }

    private static String classifyError(String raw, int http) {
        String s = raw == null ? "" : raw;
        if (s.contains("\"code\":\"insufficient_quota\"") || s.contains("insufficient_quota")) return "quota";
        if (http == 401 || s.contains("invalid_api_key")) return "auth";
        if (http == 404) return "not_found";
        if (http == 429 || s.contains("rate")) return "rate_limit";
        return "unknown";
    }
}