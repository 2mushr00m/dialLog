package com.example.diallog.auth;

import android.content.Context;
import android.os.Build;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.TimeUnit;

import java.util.function.LongSupplier;

public final class GoogleOauth implements AuthTokenProvider {
    private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final String DEFAULT_TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final Context app;
    private final String clientEmail;
    private final PrivateKey privateKey;
    private final String tokenUrl;
    private final LongSupplier clock;

    private String cachedToken;
    private long expiryEpochMs;

    public GoogleOauth(Context app, int rawServiceAccountJson) throws Exception {
        this(app, rawServiceAccountJson, DEFAULT_TOKEN_URL, System::currentTimeMillis);
    }

    public GoogleOauth(Context app, int rawServiceAccountJson,
                       String tokenUrl, LongSupplier clock) throws Exception {
        this.app = app.getApplicationContext();
        this.tokenUrl = tokenUrl;
        this.clock = clock;

        try (InputStream in = app.getResources().openRawResource(rawServiceAccountJson)) {
            byte[] buf = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                buf = in.readAllBytes();
            }
            JSONObject json = new JSONObject(new String(buf));
            clientEmail = json.getString("client_email");
            String pkcs8 = json.getString("private_key")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.decode(pkcs8, Base64.DEFAULT);
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        }
    }

    @Override
    public synchronized void invalidate() {
        cachedToken = null;
        expiryEpochMs = 0L;
    }
    @Override
    public synchronized String getToken() throws Exception {
        long now = clock.getAsLong();
        if (cachedToken != null && now < expiryEpochMs - 60_000) {
            return cachedToken;
        }

        // JWT 생성
        long iat = TimeUnit.MILLISECONDS.toSeconds(now);
        long exp = iat + 3600; // 1시간
        String header = Base64.encodeToString(
                "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        String payload = new JSONObject()
                .put("iss", clientEmail)
                .put("scope", SCOPE)
                .put("aud", tokenUrl)
                .put("exp", exp)
                .put("iat", iat)
                .toString();
        String payloadEnc = b64Url(payload.getBytes(StandardCharsets.UTF_8));
        String unsigned = header + "." + payloadEnc;

        // RS256 서명
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(unsigned.getBytes(StandardCharsets.UTF_8));
        String signature = b64Url(sig.sign());

        String jwt = unsigned + "." + signature;

        // 토큰 교환
        String body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + jwt;
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) new java.net.URL(tokenUrl).openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.getOutputStream().write(bodyBytes);

        try (InputStream in = conn.getInputStream()) {
            String resp = new String(readAll(in), StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(resp);
            cachedToken = obj.getString("access_token");
            int expiresIn = obj.getInt("expires_in");
            expiryEpochMs = now + expiresIn * 1000L;
            return cachedToken;
        }
    }

    private static String b64Url(byte[] in) {
        return Base64.encodeToString(in, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            bout.write(buf, 0, n);
        }
        return bout.toByteArray();
    }

    private static PrivateKey parsePkcs8(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.decode(clean, Base64.DEFAULT);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}