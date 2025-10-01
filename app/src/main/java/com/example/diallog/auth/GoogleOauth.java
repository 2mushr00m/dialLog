package com.example.diallog.auth;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class GoogleOauth implements AuthTokenProvider {
    private static final String TAG = "OAuth";
    private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final String DEFAULT_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final int TOKEN_TIMEOUT_MS = 30_000;

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
                       String overrideTokenUrl, LongSupplier clock) throws Exception {
        Objects.requireNonNull(app, "Context must not be null");
        ServiceAccount account = loadServiceAccount(app, rawServiceAccountJson);
        this.clientEmail = account.clientEmail;
        this.privateKey = account.privateKey;
        String resolvedTokenUrl = overrideTokenUrl;
        if (resolvedTokenUrl == null || resolvedTokenUrl.isEmpty()) {
            resolvedTokenUrl = account.tokenUri != null && !account.tokenUri.isEmpty()
                    ? account.tokenUri
                    : DEFAULT_TOKEN_URL;
        }
        this.tokenUrl = resolvedTokenUrl;
        this.clock = clock != null ? clock : System::currentTimeMillis;
        Log.i(TAG, "초기화: tokenUrl=" + this.tokenUrl);
    }

    private static final class ServiceAccount {
        final String clientEmail;
        final PrivateKey privateKey;
        final String tokenUri;

        ServiceAccount(String clientEmail, PrivateKey privateKey, String tokenUri) {
            this.clientEmail = clientEmail;
            this.privateKey = privateKey;
            this.tokenUri = tokenUri;
        }
    }


    private ServiceAccount loadServiceAccount(Context app, int rawServiceAccountJson) throws Exception {
        try (InputStream in = app.getResources().openRawResource(rawServiceAccountJson)) {

            byte[] jsonBytes = readAll(in);
            if (jsonBytes.length == 0) {
                throw new IOException("Service account JSON is empty");
            }
            JSONObject json = new JSONObject(new String(jsonBytes, StandardCharsets.UTF_8));
            String email = json.optString("client_email", null);
            if (email == null || email.isEmpty()) {
                throw new IllegalStateException("Service account JSON missing client_email");
            }
            String privateKeyPem = json.optString("private_key", null);
            if (privateKeyPem == null || privateKeyPem.isEmpty()) {
                throw new IllegalStateException("Service account JSON missing private_key");
            }
            PrivateKey key = parsePrivateKey(privateKeyPem);
            String tokenUri = json.optString("token_uri", null);
            return new ServiceAccount(email, key, tokenUri);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid service account JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void invalidate() {
        Log.i(TAG, "캐시 무효화: 상태=cleared");
        cachedToken = null;
        expiryEpochMs = 0L;
    }

    @Override
    public synchronized String getToken() throws Exception {
        long now = clock.getAsLong();
        if (cachedToken != null && now < expiryEpochMs - 60_000L) {
            Log.d(TAG, "토큰 재사용: expiresInMs=" + (expiryEpochMs - now));
            return cachedToken;
        }

        Log.i(TAG, "토큰 갱신: cached=" + (cachedToken != null));
        long issuedAt = TimeUnit.MILLISECONDS.toSeconds(now);
        long expiresAt = issuedAt + 3600L;

        String header = b64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = new JSONObject()
                .put("iss", clientEmail)
                .put("scope", SCOPE)
                .put("aud", tokenUrl)
                .put("exp", expiresAt)
                .put("iat", issuedAt)
                .toString();
        String unsigned = header + '.' + b64Url(payload.getBytes(StandardCharsets.UTF_8));

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(unsigned.getBytes(StandardCharsets.UTF_8));
        String jwt = unsigned + '.' + b64Url(signature.sign());

        HttpURLConnection connection = (HttpURLConnection) new URL(tokenUrl).openConnection();
        try {
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TOKEN_TIMEOUT_MS);
            connection.setReadTimeout(TOKEN_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion="
                    + URLEncoder.encode(jwt, "UTF-8");
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int code = connection.getResponseCode();
            Log.i(TAG, "토큰 응답: httpCode=" + code);
            InputStream responseStream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody;
            if (responseStream != null) {
                try (InputStream in = responseStream) {
                    responseBody = new String(readAll(in), StandardCharsets.UTF_8);
                }
            } else {
                responseBody = "";
            }

            if (code < 200 || code >= 300) {
                Log.e(TAG, "토큰 요청 실패: httpCode=" + code);
                throw new IOException("Token exchange failed: HTTP " + code
                        + (responseBody.isEmpty() ? "" : " - " + responseBody));
            }

            try {
                JSONObject json = new JSONObject(responseBody);
                String token = json.getString("access_token");
                int expiresIn = json.optInt("expires_in", 3600);
                cachedToken = token;
                long refreshedAt = clock.getAsLong();
                expiryEpochMs = refreshedAt + expiresIn * 1000L;
                Log.i(TAG, "토큰 발급: expiresInSec=" + expiresIn
                        + " tokenLength=" + (token != null ? token.length() : 0));
                return token;
            } catch (JSONException e) {
                Log.e(TAG, "응답 파싱 실패: 원인=" + e.getMessage(), e);
                throw new IOException("Failed to parse OAuth response: " + e.getMessage(), e);
            }
        } finally {
            connection.disconnect();
            Log.d(TAG, "연결 종료: endpoint=token");
        }
    }

    private static String b64Url(byte[] data) {
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }


    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            bout.write(buf, 0, n);
        }
        return bout.toByteArray();
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.decode(clean, Base64.DEFAULT);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}