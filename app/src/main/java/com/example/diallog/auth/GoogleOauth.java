package com.example.diallog.auth;

import android.content.Context;
import android.os.Build;
import android.util.Base64;

import org.json.JSONObject;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

public final class GoogleOauth implements AuthTokenProvider {
    private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final Context app;
    private final String clientEmail;
    private final PrivateKey privateKey;

    private String cachedToken;
    private long expiryEpochMs;

    public GoogleOauth(Context app, int rawServiceAccountJson) throws Exception {
        this.app = app.getApplicationContext();

        // 서비스계정 JSON을 raw 리소스에서 로드 (데모 전용, 배포 금지)
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
    public synchronized String getToken() throws Exception {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < expiryEpochMs - 60_000) {
            return cachedToken;
        }

        // JWT 생성
        long iat = TimeUnit.MILLISECONDS.toSeconds(now);
        long exp = iat + 3600; // 1시간
        String header = Base64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(), Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
        String payload = new JSONObject()
                .put("iss", clientEmail)
                .put("scope", SCOPE)
                .put("aud", TOKEN_URL)
                .put("exp", exp)
                .put("iat", iat)
                .toString();
        String payloadEnc = Base64.encodeToString(payload.getBytes(), Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
        String unsigned = header + "." + payloadEnc;

        // RS256 서명
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(unsigned.getBytes());
        String signature = Base64.encodeToString(sig.sign(), Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);

        String jwt = unsigned + "." + signature;

        // 토큰 교환
        String body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + jwt;
        byte[] bodyBytes = body.getBytes();
        HttpsURLConnection conn = (HttpsURLConnection) new java.net.URL(TOKEN_URL).openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.getOutputStream().write(bodyBytes);

        try (InputStream in = conn.getInputStream()) {
            String resp = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                resp = new String(in.readAllBytes());
            }
            JSONObject obj = new JSONObject(resp);
            cachedToken = obj.getString("access_token");
            int expiresIn = obj.getInt("expires_in");
            expiryEpochMs = now + expiresIn * 1000L;
            return cachedToken;
        }
    }
}