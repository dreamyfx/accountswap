package dev.dreamyfx.accountswap.util;

import com.google.gson.Gson;
import dev.dreamyfx.accountswap.AccountSwapMod;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpUtil {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Gson GSON = new Gson();

    // ── String responses ──────────────────────────────────────────────────────

    public static String postForm(String url, String formBody) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .timeout(Duration.ofSeconds(20))
                .build());
    }

    public static String postJson(String url, String jsonBody) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(20))
                .build());
    }

    public static String getWithBearer(String url, String token) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(20))
                .build());
    }

    public static String get(String url) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(20))
                .build());
    }

    // ── Gson-deserialized responses (null on any failure) ─────────────────────

    public static <T> T postFormGson(String url, String formBody, Class<T> type) {
        try {
            String body = postForm(url, formBody);
            return GSON.fromJson(body, type);
        } catch (Exception e) {
            AccountSwapMod.LOGGER.warn("postFormGson failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    public static <T> T postJsonGson(String url, String jsonBody, Class<T> type) {
        try {
            String body = postJson(url, jsonBody);
            return GSON.fromJson(body, type);
        } catch (Exception e) {
            AccountSwapMod.LOGGER.warn("postJsonGson failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    public static <T> T getJsonGson(String url, String bearerToken, Class<T> type) {
        try {
            String body = getWithBearer(url, bearerToken);
            return GSON.fromJson(body, type);
        } catch (Exception e) {
            AccountSwapMod.LOGGER.warn("getJsonGson failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    // ── Bytes / stream ────────────────────────────────────────────────────────

    public static byte[] getBytes(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new java.io.IOException("HTTP " + resp.statusCode() + " from " + url);
        }
        return resp.body();
    }

    public static InputStream getStream(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new java.io.IOException("HTTP " + resp.statusCode() + " from " + url);
        }
        return resp.body();
    }

    private static String send(HttpRequest req) throws Exception {
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        AccountSwapMod.LOGGER.debug("HTTP {} {} -> {}", req.method(), req.uri(), resp.statusCode());
        return resp.body();
    }
}
