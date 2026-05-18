package com.werewolf.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;

/**
 * Client HTTP léger pour l'API REST PHP.
 * Toutes les requêtes sont SYNCHRONES : appelez-les depuis un Task ou un Timeline,
 * jamais directement sur le thread JavaFX.
 */
public class ApiClient {

    /** À adapter selon votre install Apache/XAMPP. */
    public static String BASE_URL = "http://127.0.0.1:8000/api";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    // ----- GET -----
    public JsonNode get(String path) throws Exception {
        HttpRequest req = baseRequest(path).GET().build();
        return send(req);
    }

    public <T> T get(String path, Class<T> type) throws Exception {
        return JSON.treeToValue(get(path), type);
    }

    // ----- POST JSON -----
    public JsonNode post(String path, Object body) throws Exception {
        String json = body == null ? "{}" : JSON.writeValueAsString(body);
        HttpRequest req = baseRequest(path)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json))
                .build();
        return send(req);
    }

    public <T> T post(String path, Object body, Class<T> type) throws Exception {
        return JSON.treeToValue(post(path, body), type);
    }

    // ----- helpers -----
    private HttpRequest.Builder baseRequest(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(8));
        if (Session.token != null) b.header("Authorization", "Bearer " + Session.token);
        return b;
    }

    private JsonNode send(HttpRequest req) throws Exception {
        HttpResponse<String> resp = HTTP.send(req, BodyHandlers.ofString());
        JsonNode tree = resp.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(resp.body());
        if (resp.statusCode() >= 400) {
            String err = tree.path("error").asText("HTTP " + resp.statusCode());
            throw new ApiException(resp.statusCode(), err);
        }
        return tree;
    }

    /** Helper pour construire un body simple {clé:valeur, ...}. */
    public static Map<String,Object> body(Object... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("kv pairs requises");
        var m = new java.util.LinkedHashMap<String,Object>();
        for (int i = 0; i < kv.length; i += 2) m.put((String)kv[i], kv[i+1]);
        return m;
    }

    public static class ApiException extends RuntimeException {
        public final int status;
        public ApiException(int s, String msg) { super(msg); this.status = s; }
    }
}
