package com.zuluindustries.rag.provider.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zuluindustries.rag.core.ChatMessage;

/**
 * Testet gegen einen kleinen lokalen Webserver, der sich wie die Chat-API
 * verhält. Es wird nichts ins Internet geschickt und es entstehen keine Kosten.
 */
class OpenAiCompatibleChatModelTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API_KEY = "test-key-123";

    private HttpServer server;
    private String baseUrl;

    private JsonNode receivedBody;
    private int status = 200;
    private String finishReason = "stop";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** Antwortet wie die echte API mit "Antwort auf: <letzte Nachricht>". */
    private void handle(HttpExchange exchange) throws IOException {
        receivedBody = JSON.readTree(exchange.getRequestBody().readAllBytes());

        String body;
        if (status == 200) {
            JsonNode messages = receivedBody.path("messages");
            String lastMessage = messages.get(messages.size() - 1).path("content").asText();
            ObjectNode response = JSON.createObjectNode();
            ObjectNode choice = response.putArray("choices").addObject();
            choice.putObject("message").put("role", "assistant").put("content", "Antwort auf: " + lastMessage);
            choice.put("finish_reason", finishReason);
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

    private OpenAiCompatibleChatModel model() {
        return new OpenAiCompatibleChatModel(baseUrl, API_KEY, "mistral-small-3.2-24b-instruct-2506", 0.2, 800,
                Duration.ofMillis(1));
    }

    @Test
    void sendsMessagesAndSettingsAndReturnsContent() {
        String answer = model().chat(List.of(
                ChatMessage.system("Antworte nur aus den Quellen."),
                ChatMessage.user("Darf ich den Sand berühren?")));

        assertEquals("Antwort auf: Darf ich den Sand berühren?", answer);
        assertEquals("mistral-small-3.2-24b-instruct-2506", receivedBody.path("model").asText());
        assertEquals("system", receivedBody.path("messages").get(0).path("role").asText());
        assertEquals("user", receivedBody.path("messages").get(1).path("role").asText());
        assertEquals(0.2, receivedBody.path("temperature").asDouble(), 1e-9);
        assertEquals(800, receivedBody.path("max_tokens").asInt());
    }

    @Test
    void marksTruncatedAnswer() {
        finishReason = "length";

        String answer = model().chat(List.of(ChatMessage.user("Erkläre alle Regeln.")));

        assertTrue(answer.endsWith(OpenAiCompatibleChatModel.TRUNCATION_NOTE));
    }

    @Test
    void reportsErrorWithoutApiKey() {
        status = 401;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> model().chat(List.of(ChatMessage.user("Hallo"))));

        assertTrue(error.getMessage().contains("HTTP 401"));
        assertFalse(error.getMessage().contains(API_KEY));
    }
}
