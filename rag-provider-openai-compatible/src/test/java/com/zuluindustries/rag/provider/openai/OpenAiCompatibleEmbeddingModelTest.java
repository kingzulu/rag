package com.zuluindustries.rag.provider.openai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Testet gegen einen kleinen lokalen Webserver, der sich wie die Embedding-API
 * verhält. Es wird nichts ins Internet geschickt und es entstehen keine Kosten.
 */
class OpenAiCompatibleEmbeddingModelTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API_KEY = "test-key-123";

    private HttpServer server;
    private String baseUrl;

    /** Was der Server empfangen hat: je Anfrage der Authorization-Header und der JSON-Body. */
    private final List<String> receivedAuthorization = new ArrayList<>();
    private final List<JsonNode> receivedBodies = new ArrayList<>();

    /** Statuscodes, die der Server nacheinander liefern soll; danach immer 200. */
    private final Deque<Integer> plannedStatuses = new ArrayDeque<>();

    /** Liefert der Server die Vektoren in umgekehrter Reihenfolge? */
    private boolean reverseOrder;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);   // Port 0 = freien Port wählen
        server.createContext("/v1/embeddings", this::handle);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /**
     * Antwortet wie die echte API. Der "Vektor" zu einem Text ist hier einfach
     * [Position in der Anfrage, Länge des Textes] – so kann man im Test prüfen,
     * welcher Vektor zu welchem Text gehört.
     */
    private void handle(HttpExchange exchange) throws IOException {
        receivedAuthorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
        JsonNode request = JSON.readTree(exchange.getRequestBody().readAllBytes());
        receivedBodies.add(request);

        int status = plannedStatuses.isEmpty() ? 200 : plannedStatuses.poll();
        String body;
        if (status == 200) {
            ObjectNode response = JSON.createObjectNode();
            ArrayNode data = response.putArray("data");
            JsonNode input = request.path("input");
            for (int n = 0; n < input.size(); n++) {
                int i = reverseOrder ? input.size() - 1 - n : n;
                ObjectNode item = data.addObject();
                item.put("index", i);
                item.putArray("embedding").add((float) i).add((float) input.get(i).asText().length());
            }
            body = response.toString();
        } else {
            body = "{\"error\": {\"message\": \"Fehler " + status + "\"}}";
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private OpenAiCompatibleEmbeddingModel model(int batchSize) {
        return new OpenAiCompatibleEmbeddingModel(baseUrl, API_KEY, "bge-multilingual-gemma2", batchSize,
                Duration.ofMillis(1));
    }

    @Test
    void sendsModelInputAndApiKey() {
        List<float[]> vectors = model(32).embed(List.of("Bunker", "Grün"));

        assertEquals(2, vectors.size());
        assertArrayEquals(new float[] { 0, 6 }, vectors.get(0));   // "Bunker" hat 6 Zeichen
        assertArrayEquals(new float[] { 1, 4 }, vectors.get(1));   // "Grün" hat 4 Zeichen

        assertEquals("Bearer " + API_KEY, receivedAuthorization.getFirst());
        JsonNode body = receivedBodies.getFirst();
        assertEquals("bge-multilingual-gemma2", body.path("model").asText());
        assertEquals("Grün", body.path("input").get(1).asText());
    }

    @Test
    void splitsTextsIntoBatches() {
        List<String> texts = IntStream.range(0, 70).mapToObj(i -> "x".repeat(i + 1)).toList();

        List<float[]> vectors = model(32).embed(texts);

        assertEquals(List.of(32, 32, 6), receivedBodies.stream().map(body -> body.path("input").size()).toList());
        assertEquals(70, vectors.size());
        assertEquals(70f, vectors.get(69)[1]);   // letzter Text hat 70 Zeichen
    }

    @Test
    void usesIndexToRestoreOrder() {
        reverseOrder = true;

        List<float[]> vectors = model(32).embed(List.of("a", "bb", "ccc"));

        assertArrayEquals(new float[] { 0, 1 }, vectors.get(0));
        assertArrayEquals(new float[] { 2, 3 }, vectors.get(2));
    }

    @Test
    void retriesWhenServerIsBusy() {
        plannedStatuses.addAll(List.of(429, 503));

        List<float[]> vectors = model(32).embed(List.of("Bunker"));

        assertEquals(1, vectors.size());
        assertEquals(3, receivedBodies.size());   // 429, 503, dann 200
    }

    @Test
    void givesUpAfterFourAttempts() {
        plannedStatuses.addAll(List.of(504, 504, 504, 504, 504));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> model(32).embed(List.of("Bunker")));

        assertEquals(4, receivedBodies.size());   // 1 Versuch + 3 Wiederholungen, der 5. wird nicht mehr geschickt
        assertTrue(error.getMessage().contains("Versuch 4"));
        assertTrue(error.getMessage().contains("HTTP 504"));
    }

    @Test
    void failsWithoutRetryOnWrongApiKey() {
        plannedStatuses.add(401);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> model(32).embed(List.of("Bunker")));

        assertEquals(1, receivedBodies.size());   // kein zweiter Versuch
        assertTrue(error.getMessage().contains("HTTP 401"));
        assertFalse(error.getMessage().contains(API_KEY), "Der API-Key darf nie in Fehlermeldungen stehen");
    }

    @Test
    void reportsMissingEnvironmentVariable() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> OpenAiCompatibleEmbeddingModel.fromEnvironment(baseUrl, "RAG_TEST_VARIABLE_DIE_ES_NICHT_GIBT",
                        "bge-multilingual-gemma2"));

        assertTrue(error.getMessage().contains("RAG_TEST_VARIABLE_DIE_ES_NICHT_GIBT"));
    }
}
