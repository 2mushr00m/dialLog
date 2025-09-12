package com.example.diallog.auth;

import com.example.diallog.R;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStreamReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.*;

public final class GoogleAuthTokenProviderTest {

    private MockWebServer server;
    private final Gson gson = new Gson();

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private GoogleAuthTokenProvider.ServiceAccount loadSa() throws Exception {
        // 테스트 리소스: app/src/test/resources/service-account.json
        try (InputStreamReader r = new InputStreamReader(
                getClass().getResourceAsStream(R.raw.service_account, "/service-account.json"))) {
            GoogleAuthTokenProvider.ServiceAccount sa =
                    gson.fromJson(r, GoogleAuthTokenProvider.ServiceAccount.class);
            // 테스트에서는 tokenUri를 Mock 서버 주소로 강제
            sa.tokenUri = server.url("/token").toString();
            return sa;
        }
    }

    @Test
    public void issueToken_success_and_cacheUntilExpiryMinus60s() throws Exception {
        // 1) 모의 토큰 응답 3600초
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"access_token\":\"ya29.token1\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"));

        OkHttpClient http = new OkHttpClient();
        GoogleAuthTokenProvider.ServiceAccount sa = loadSa();
        GoogleAuthTokenProvider.TokenCache cache = new GoogleAuthTokenProvider.MemoryTokenCache();

        // 고정 시간: 2025-09-12T00:00:00Z
        Clock fixed = Clock.fixed(Instant.parse("2025-09-12T00:00:00Z"), ZoneOffset.UTC);

        GoogleAuthTokenProvider p = new GoogleAuthTokenProvider(
                http, sa, "https://www.googleapis.com/auth/cloud-platform", cache, fixed);

        String t1 = p.getAccessToken();
        assertEquals("ya29.token1", t1);
        assertTrue(cache.expiresAt() > 0);

        // 2) 캐시 사용 확인(만료 60초 이전이므로 재호출 없음)
        String t2 = p.getAccessToken();
        assertSame(t1, t2);

        // 3) 만료 임계치로 시계 이동(T-59초) → 재발급 필요
        Clock nearExpiry = Clock.fixed(Instant.parse("2025-09-12T00:59:02Z"), ZoneOffset.UTC); // 353? adjust: 3600-58
        // 새 응답 준비
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"access_token\":\"ya29.token2\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"));

        GoogleAuthTokenProvider p2 = new GoogleAuthTokenProvider(
                http, sa, "https://www.googleapis.com/auth/cloud-platform", cache, nearExpiry);

        String t3 = p2.getAccessToken();
        assertEquals("ya29.token2", t3);
    }

    @Test(expected = IllegalStateException.class)
    public void issueToken_httpError_throws() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_grant\"}"));
        OkHttpClient http = new OkHttpClient();
        GoogleAuthTokenProvider.ServiceAccount sa = loadSa();
        GoogleAuthTokenProvider.TokenCache cache = new GoogleAuthTokenProvider.MemoryTokenCache();
        Clock fixed = Clock.fixed(Instant.parse("2025-09-12T00:00:00Z"), ZoneOffset.UTC);

        GoogleAuthTokenProvider p = new GoogleAuthTokenProvider(
                http, sa, "https://www.googleapis.com/auth/cloud-platform", cache, fixed);

        p.getAccessToken(); // 예외 기대
    }

    // 실제 호출 예시. 비밀키가 유효하고 네트워크 허용 환경에서만 사용.
    // @Ignore 제거 시 실서버 호출. 커밋 금지.
    // @Test @org.junit.Ignore
    public void realCall_example() throws Exception {
        OkHttpClient http = new OkHttpClient();
        GoogleAuthTokenProvider.ServiceAccount sa;
        try (InputStreamReader r = new InputStreamReader(
                getClass().getResourceAsStream("/service-account-real.json"))) {
            sa = gson.fromJson(r, GoogleAuthTokenProvider.ServiceAccount.class);
        }
        GoogleAuthTokenProvider.TokenCache cache = new GoogleAuthTokenProvider.MemoryTokenCache();
        Clock now = Clock.systemUTC();

        GoogleAuthTokenProvider p = new GoogleAuthTokenProvider(
                http, sa, "https://www.googleapis.com/auth/cloud-platform", cache, now);

        String token = p.getAccessToken();
        assertNotNull(token);
        System.out.println("access_token=" + token.substring(0, Math.min(12, token.length())) + "...");
    }
}