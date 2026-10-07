package com.zuluindustries.rag.provider.openai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zuluindustries.rag.core.EmbeddingModel;

/**
 * Embeddings über eine OpenAI-kompatible API ({@code POST <baseUrl>/embeddings}).
 * Funktioniert mit allen Anbietern, die dieses Format sprechen, z. B. Scaleway
 * ({@code https://api.scaleway.ai/v1}), OpenAI oder Mistral.
 *
 * <p>Texte werden in Paketen ("Batches") verschickt statt einzeln. HTTP,
 * API-Key und Wiederholungen übernimmt {@link OpenAiCompatibleClient}.
 */
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    public static final int DEFAULT_BATCH_SIZE = 32;

    private final OpenAiCompatibleClient client;
    private final String model;
    private final int batchSize;

    public OpenAiCompatibleEmbeddingModel(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, DEFAULT_BATCH_SIZE, OpenAiCompatibleClient.DEFAULT_RETRY_DELAY);
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
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize muss mindestens 1 sein");
        }
        this.client = new OpenAiCompatibleClient(baseUrl, apiKey, retryDelay);
        this.model = model;
        this.batchSize = batchSize;
    }

    /** Wie der Konstruktor, aber der API-Key kommt aus einer Umgebungsvariable. */
    public static OpenAiCompatibleEmbeddingModel fromEnvironment(String baseUrl, String apiKeyVariable, String model) {
        return new OpenAiCompatibleEmbeddingModel(baseUrl, OpenAiCompatibleClient.apiKeyFromEnvironment(apiKeyVariable),
                model);
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

    /** Schickt {"model": "...", "input": ["Text 1", "Text 2", ...]}. */
    private List<float[]> embedBatch(List<String> batch) {
        ObjectNode request = OpenAiCompatibleClient.JSON.createObjectNode();
        request.put("model", model);
        ArrayNode input = request.putArray("input");
        batch.forEach(input::add);

        return parseResponse(client.post("/embeddings", request), batch.size());
    }

    /**
     * Liest {"data": [{"index": 0, "embedding": [0.1, ...]}, ...]}. Die Vektoren
     * werden anhand von "index" einsortiert, nicht nach ihrer Reihenfolge in der Antwort.
     */
    private static List<float[]> parseResponse(JsonNode response, int expectedCount) {
        JsonNode data = response.path("data");
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
}
