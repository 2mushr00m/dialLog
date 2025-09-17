package com.example.diallog;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

import android.content.Context;
import android.util.Base64;

import androidx.test.core.app.ApplicationProvider;

import com.example.diallog.auth.GoogleOauth;

public final class GoogleOauthTest {
    private MockWebServer server;
    private Context app;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        app = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private static final class FakeClock implements java.util.function.LongSupplier {
        private final AtomicLong now = new AtomicLong();
        @Override public long getAsLong() { return now.get(); }
        void set(long v) { now.set(v); }
    }

    @Test
    public void getToken_cachesAndRefreshesNearExpiry_andJwtPayload() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"access_token\":\"t1\",\"expires_in\":3600}"));

        FakeClock clock = new FakeClock();
        clock.set(0L);
        GoogleOauth oauth = new GoogleOauth(app, R.raw.service_account,
                server.url("/token").toString(), clock);

        String t1 = oauth.getToken();
        assertEquals("t1", t1);

        String t2 = oauth.getToken();
        assertEquals("t1", t2);
        assertEquals(1, server.getRequestCount());

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer"));
        String assertion = URLDecoder.decode(body.substring(body.indexOf("assertion=") + 10),
                StandardCharsets.UTF_8);
        String[] parts = assertion.split("\\.");
        String headerJson = new String(Base64.decode(parts[0], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
        assertTrue(headerJson.contains("\"alg\":\"RS256\""));
        String payloadJson = new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
        JSONObject payload = new JSONObject(payloadJson);
        assertEquals("test@example.iam.gserviceaccount.com", payload.getString("iss"));

        clock.set(3_600_000L - 59_000L);
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"access_token\":\"t2\",\"expires_in\":3600}"));

        String t3 = oauth.getToken();
        assertEquals("t2", t3);
        assertEquals(2, server.getRequestCount());
    }

    @Test(expected = Exception.class)
    public void getToken_httpErrorThrows() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(400));
        FakeClock clock = new FakeClock();
        clock.set(0L);
        GoogleOauth oauth = new GoogleOauth(app, R.raw.service_account,
                server.url("/token").toString(), clock);
        oauth.getToken();
    }
}