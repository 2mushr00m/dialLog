package com.example.diallog.auth;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import okhttp3.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;



public final class GoogleAuthTokenProvider {

    public interface TokenCache {
        String get();
        void put(String token, long expiresAtEpochSec);
        long expiresAt();
    }

    public static final class MemoryTokenCache implements TokenCache {
        private String token;
        private long exp;
        public String get() { return token; }
        public void put(String t, long e) { token = t; exp = e; }
        public long expiresAt() { return exp; }
    }

    public static final class ServiceAccount {
        @SerializedName("client_email") public String clientEmail;
        @SerializedName("private_key")  public String privateKeyPem;
        @SerializedName("token_uri")    public String tokenUri; // 보통 https://oauth2.googleapis.com/token
    }

    private final OkHttpClient http;
    private final Gson gson = new Gson();
    private final ServiceAccount sa;
    private final String scope;              // 예: "https://www.googleapis.com/auth/cloud-platform"
    private final TokenCache cache;
    private final Clock clock;

    public GoogleAuthTokenProvider(OkHttpClient http, ServiceAccount sa, String scope,
                                   TokenCache cache, Clock clock) {
        this.http = http;
        this.sa = sa;
        this.scope = scope;
        this.cache = cache;
        this.clock = clock;
    }

    /** 토큰 획득. 만료 60초 전이면 재발급 */
    public synchronized String getAccessToken() throws Exception {
        long now = clock.instant().getEpochSecond();
        String cached = cache.get();
        if (cached != null && cache.expiresAt() - now > 60) return cached;

        String assertion = signJwt(sa, scope, now);
        TokenResponse tr = exchange(sa.tokenUri, assertion);
        long expiresAt = now + tr.expiresIn;
        cache.put(tr.accessToken, expiresAt);
        return tr.accessToken;
    }

    /** RS256 서비스계정 JWT 생성 */
    static String signJwt(ServiceAccount sa, String scope, long nowEpochSec) throws Exception {
        long exp = nowEpochSec + 3600; // 1시간
        Map<String, Object> header = new HashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");

        Map<String, Object> claim = new HashMap<>();
        claim.put("iss", sa.clientEmail);
        claim.put("scope", scope);
        claim.put("aud", sa.tokenUri); // aud=token_uri
        claim.put("iat", nowEpochSec);
        claim.put("exp", exp);

        String headerB64 = b64Url(gsonStatic().toJson(header).getBytes(StandardCharsets.UTF_8));
        String claimB64  = b64Url(gsonStatic().toJson(claim).getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + claimB64;

        PrivateKey key = parsePkcs8PrivateKey(sa.privateKeyPem);
        byte[] sig = signRs256(signingInput.getBytes(StandardCharsets.UTF_8), key);
        String sigB64 = b64Url(sig);

        return signingInput + "." + sigB64;
    }

    private static byte[] signRs256(byte[] data, PrivateKey key) throws Exception {
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(key);
        s.update(data);
        return s.sign();
    }

    private static PrivateKey parsePkcs8PrivateKey(String pem) throws Exception {
        // -----BEGIN PRIVATE KEY----- ~ -----END PRIVATE KEY-----
        String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(clean);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static String b64Url(byte[] in) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(in);
    }

    /** JWT Bearer로 토큰 교환 */
    private TokenResponse exchange(String tokenUri, String assertion) throws Exception {
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .add("assertion", assertion)
                .build();

        Request req = new Request.Builder()
                .url(tokenUri)
                .post(body)
                .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                throw new IllegalStateException("Token HTTP " + res.code());
            }
            String json = res.body().string();
            TokenResponse tr = gson.fromJson(json, TokenResponse.class);
            if (tr == null || tr.accessToken == null || tr.expiresIn <= 0) {
                throw new IllegalStateException("Invalid token response");
            }
            return tr;
        }
    }

    static final class TokenResponse {
        @SerializedName("access_token") String accessToken;
        @SerializedName("expires_in")   long   expiresIn;
        @SerializedName("token_type")   String tokenType;
    }

    // Gson 싱글턴 최소화
    private static Gson gsonStatic() { return Holder.G; }
    private static final class Holder { static final Gson G = new Gson(); }
}
