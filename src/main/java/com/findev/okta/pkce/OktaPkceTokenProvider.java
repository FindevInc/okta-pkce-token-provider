package com.findev.okta.pkce;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.UUID;

import static java.util.Collections.singleton;

public class OktaPkceTokenProvider {

    public static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient client;

    private final Gson gson = new GsonBuilder().create();
    private final String oktaUrl;
    private final String oktaUsername;
    private final String oktaPassword;
    private final LoadingCache<Object, String> tokenCache;
    private final String targetClientId;
    private final String identityZoneId;
    private final String loginRedirectUri;

    public OktaPkceTokenProvider(String oktaUrl,
                                 String oktaUsername,
                                 String oktaPassword,
                                 String identityZoneId,
                                 String targetClientId,
                                 String loginRedirectUri) throws Exception {
        this(OkHttpClientProvider.createHttpClient(),
                oktaUrl, oktaUsername, oktaPassword, identityZoneId,
                targetClientId, loginRedirectUri, Duration.ofSeconds(3500));
    }

    public OktaPkceTokenProvider(OkHttpClient client,
                                 String oktaUrl,
                                 String oktaUsername,
                                 String oktaPassword,
                                 String identityZoneId,
                                 String targetClientId,
                                 String loginRedirectUri,
                                 Duration expireAfter) {
        this.identityZoneId = identityZoneId;
        this.loginRedirectUri = loginRedirectUri;
        this.targetClientId = targetClientId;
        this.client = client;
        this.oktaUrl = oktaUrl;
        this.oktaUsername = oktaUsername;
        this.oktaPassword = oktaPassword;
        this.tokenCache = CacheBuilder.newBuilder()
                .expireAfterAccess(expireAfter)
                .build(CacheLoader.from(() -> {
                    try {
                        Pair<String, String> codeAndChallenge = generateCodeChallenge();
                        String sessionToken = getSessionToken();
                        String authCode = getAuthorizationCode(sessionToken, codeAndChallenge.getValue());
                        return getAuthToken(authCode, codeAndChallenge.getKey());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
    }

    public String getToken() throws Exception {
        return getToken("key");
    }

    public String getToken(String key) throws Exception {
        return tokenCache.get(key);
    }

    public String getNewToken() throws Exception {
        return getNewToken("key");
    }

    public String getNewToken(String key) throws Exception {
        expireKeys(singleton(key));
        return tokenCache.get(key);
    }

    public void expireAll() {
        tokenCache.invalidateAll();
    }

    public void expireKeys(Collection<String> keys) {
        tokenCache.invalidateAll(keys);
    }


    private String getSessionToken() throws Exception {
        Request request = new Request.Builder()
                .url(oktaUrl + "/api/v1/authn")
                .post(RequestBody.create(JSON, gson.toJson(ImmutableMap.of(
                        "username", oktaUsername,
                        "password", oktaPassword,
                        "options", ImmutableMap.of(
                                "multiOptionalFactorEnroll", false,
                                "warnBeforePasswordExpired", false
                        )
                ))))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return JsonParser.parseString(response.body().string()).getAsJsonObject().get("sessionToken").getAsString();
        }
    }

    private String getAuthorizationCode(String sessionToken, String codeChallenge) throws Exception {
        Request request = new Request.Builder().url(
                        HttpUrl.parse(oktaUrl + "/oauth2/" + identityZoneId + "/v1/authorize")
                                .newBuilder()
                                .addQueryParameter("client_id", targetClientId)
                                .addQueryParameter("code_challenge", codeChallenge)
                                .addQueryParameter("code_challenge_method", "S256")
                                .addQueryParameter("nonce", UUID.randomUUID().toString())
                                .addQueryParameter("redirect_uri", loginRedirectUri)
                                .addQueryParameter("response_type", "code")
                                .addQueryParameter("state", loginRedirectUri)
                                .addQueryParameter("scope", "openid profile offline_access email")
                                .addQueryParameter("response_mode", "form_post")
                                .addQueryParameter("sessionToken", sessionToken)
                                .build()
                                .toString())
                .build();
        try (Response response = client.newCall(request).execute()) {
            String res = response.body().string();
            return Jsoup.parse(res).select("#appForm input[name=\"code\"]").first().val();
        }
    }

    private String getAuthToken(String pomsAuthorizationCode, String codeChallenge) throws Exception {
        Request request = new Request.Builder()
                .url(oktaUrl + "/oauth2/" + identityZoneId + "/v1/token")
                .post(new FormBody.Builder()
                        .add("client_id", targetClientId)
                        .add("redirect_uri", loginRedirectUri)
                        .add("grant_type", "authorization_code")
                        .add("code_verifier", codeChallenge)
                        .add("code", pomsAuthorizationCode)
                        .build())
                .build();
        try (Response response = client.newCall(request).execute()) {
            return JsonParser.parseString(response.body().string()).getAsJsonObject().get("access_token").getAsString();
        }
    }

    private Pair<String, String> generateCodeChallenge() {
        String code = RandomStringUtils.randomAlphabetic(50);
        byte[] sha256 = Hashing.sha256().hashString(code, StandardCharsets.UTF_8).asBytes();
        String hash = BaseEncoding.base64Url().encode(sha256);
        return Pair.of(code, hash.substring(0, hash.length() - 1));
    }
}