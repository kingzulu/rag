package com.zuluindustries.rag.embedding.openai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zuluindustries.rag.core.EmbeddingModel;

/**
 * Embeddings über eine OpenAI-kompatible API ({@code POST <baseUrl>/embeddings}).
 * Funktioniert mit allen Anbietern, die dieses Format sprechen, z. B. Scaleway
 * ({@code https://api.scaleway.ai/v1}), OpenAI oder Mistral.
 *
 * <ul>
 * <li>Texte werden in Paketen ("Batches") verschickt statt einzeln.</li>
 * <li>Bei Überlastung (HTTP 429) oder Serverfehlern (5xx) wird mit wachsender
 * Pause erneut versucht.</li>
 * <li>Der API-Key erscheint nie in Fehlermeldungen.</li>
 * </ul>
 */
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    public static final int DEFAULT_BATCH_SIZE = 32;
    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(2);

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_ERROR_BODY_LENGTH = 500;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final int batchSize;
    private final Duration retryDelay;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OpenAiCompatibleEmbeddingModel(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, DEFAULT_BATCH_SIZE, DEFAULT_RETRY_DELAY);
    }

    /**
     * @param baseUrl    Basisadresse der API, z. B. "https://api.scaleway.ai/v1"
     * @param apiKey     der geheime Schlüssel
     * @param model      Name des Embedding-Modells, z. B. "bge-multilingual-gemma2"
     * @param batchSize  höchstens so viele Texte pro Anfrage
     * @param retryDelay Pause vor dem ersten Wiederholungsversuch (verdoppelt sich danach)
     */
    public OpenAiCompatibleEmbeddingModel(String baseUrl, String apiKey, String model, int batchSize,
            Duration retryDelay) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API-Key fehlt");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize muss mindestens 1 sein");
        }
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/embeddings");
        this.apiKey = apiKey;
        this.model = model;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
    }

    /**
     * Liest den API-Key aus einer Umgebungsvariable.
     *
     * @throws IllegalStateException wenn die Variable nicht gesetzt ist – mit Hinweis, wo man sie setzt
     */
    public static OpenAiCompatibleEmbeddingModel fromEnvironment(String baseUrl, String apiKeyVariable, String model) {
        String apiKey = System.getenv(apiKeyVariable);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Umgebungsvariable " + apiKeyVariable + " ist nicht gesetzt. "
                    + "In Eclipse: Run Configurations → Reiter \"Environment\" → Add…");
        }
        return new OpenAiCompatibleEmbeddingModel(baseUrl, apiKey, model);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(start + batchSize, texts.size()));
            vectors.addAll(embedBatch(batch));
        }
        return vectors;
    }

    private List<float[]> embedBatch(List<String> batch) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(batch)))
                .build();

        for (int attempt = 1;; attempt++) {
            HttpResponse<String> response = send(request);
            int status = response.statusCode();
            if (status == 200) {
                return parseResponse(response.body(), batch.size());
            }
            boolean retryable = status == 429 || status >= 500;
            if (!retryable || attempt == MAX_ATTEMPTS) {
                throw new IllegalStateException("Embedding-Anfrage fehlgeschlagen (Versuch " + attempt + "): HTTP "
                        + status + " – " + abbreviate(response.body()));
            }
            pause(retryDelay.multipliedBy(1L << (attempt - 1)));   // 2 s, 4 s, …
        }
    }

    /** Baut {"model": "...", "input": ["Text 1", "Text 2", ...]}. */
    private String requestBody(List<String> batch) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", model);
        ArrayNode input = root.putArray("input");
        batch.forEach(input::add);
        return root.toString();
    }

    /**
     * Liest {"data": [{"index": 0, "embedding": [0.1, ...]}, ...]}. Die Vektoren
     * werden anhand von "index" einsortiert, nicht nach ihrer Reihenfolge in der Antwort.
     */
    private static List<float[]> parseResponse(String body, int expectedCount) {
        JsonNode data;
        try {
            data = JSON.readTree(body).path("data");
        } catch (IOException e) {
            throw new IllegalStateException("Antwort ist kein gültiges JSON: " + abbreviate(body), e);
        }
        if (!data.isArray() || data.size() != expectedCount) {
            throw new IllegalStateException("Unerwartete Antwort: " + expectedCount + " Vektoren erwartet, "
                    + data.size() + " erhalten");
        }

        float[][] vectors = new float[expectedCount][];
        for (JsonNode item : data) {
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= expectedCount || vectors[index] != null) {
                throw new IllegalStateException("Ungültiger oder doppelter Index in der Antwort: " + index);
            }
            JsonNode embedding = item.path("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = embedding.get(i).floatValue();
            }
            vectors[index] = vector;
        }
        return Arrays.asList(vectors);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("Verbindung zu " + endpoint + " fehlgeschlagen", e);
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

    private static String abbreviate(String text) {
        return text.length() <= MAX_ERROR_BODY_LENGTH ? text : text.substring(0, MAX_ERROR_BODY_LENGTH) + " …";
    }
}
