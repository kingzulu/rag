package com.zuluindustries.rag.provider.openai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Gemeinsame HTTP-Anbindung für OpenAI-kompatible APIs: schickt JSON per POST,
 * setzt den API-Key, wiederholt bei Überlastung (HTTP 429) oder Serverfehlern
 * (5xx) mit wachsender Pause und erzeugt Fehlermeldungen ohne den API-Key.
 */
public class OpenAiCompatibleClient {

    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(2);

    static final ObjectMapper JSON = new ObjectMapper();

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final String baseUrl;
    private final String apiKey;
    private final Duration retryDelay;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * @param baseUrl    Basisadresse der API, z. B. "https://api.scaleway.ai/v1"
     * @param apiKey     der geheime Schlüssel
     * @param retryDelay Pause vor dem ersten Wiederholungsversuch (verdoppelt sich danach)
     */
    public OpenAiCompatibleClient(String baseUrl, String apiKey, Duration retryDelay) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API-Key fehlt");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.retryDelay = retryDelay;
    }

    /**
     * Liest den API-Key aus einer Umgebungsvariable.
     *
     * @throws IllegalStateException wenn die Variable nicht gesetzt ist – mit Hinweis, wo man sie setzt
     */
    public static String apiKeyFromEnvironment(String variable) {
        String apiKey = System.getenv(variable);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Umgebungsvariable " + variable + " ist nicht gesetzt. "
                    + "In Eclipse: Run Configurations → Reiter \"Environment\" → Add…");
        }
        return apiKey;
    }

    /**
     * Schickt {@code body} per POST an {@code <baseUrl><path>} und liefert die JSON-Antwort.
     *
     * @param path z. B. "/embeddings" oder "/chat/completions"
     */
    public JsonNode post(String path, ObjectNode body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        for (int attempt = 1;; attempt++) {
            HttpResponse<String> response = send(request);
            int status = response.statusCode();
            if (status == 200) {
                return parse(response.body());
            }
            boolean retryable = status == 429 || status >= 500;
            if (!retryable || attempt == MAX_ATTEMPTS) {
                throw new IllegalStateException("Anfrage an " + path + " fehlgeschlagen (Versuch " + attempt
                        + "): HTTP " + status + " – " + abbreviate(response.body()));
            }
            pause(retryDelay.multipliedBy(1L << (attempt - 1)));   // 2 s, 4 s, …
        }
    }

    private static JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("Antwort ist kein gültiges JSON: " + abbreviate(body), e);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("Verbindung zu " + request.uri() + " fehlgeschlagen", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Abgebrochen", e);
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Abgebrochen", e);
        }
    }

    static String abbreviate(String text) {
        return text.length() <= MAX_ERROR_BODY_LENGTH ? text : text.substring(0, MAX_ERROR_BODY_LENGTH) + " …";
    }
}
