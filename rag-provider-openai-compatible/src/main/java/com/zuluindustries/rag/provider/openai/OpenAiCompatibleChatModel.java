package com.zuluindustries.rag.provider.openai;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zuluindustries.rag.core.ChatMessage;
import com.zuluindustries.rag.core.ChatModel;

/**
 * Chat über eine OpenAI-kompatible API ({@code POST <baseUrl>/chat/completions}).
 *
 * <ul>
 * <li>{@code temperature}: 0 = möglichst gleichbleibende, nüchterne Antworten,
 * höher = abwechslungsreicher. Für Regelauskünfte niedrig wählen.</li>
 * <li>{@code maxTokens}: Obergrenze für die Länge der Antwort (schützt auch vor
 * unerwarteten Kosten). Wird sie erreicht, bekommt die Antwort einen Hinweis.</li>
 * </ul>
 */
public class OpenAiCompatibleChatModel implements ChatModel {

    static final String TRUNCATION_NOTE = "\n\n[Hinweis: Die Antwort wurde wegen der Längenbegrenzung abgeschnitten.]";

    private final OpenAiCompatibleClient client;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    /**
     * @param baseUrl     Basisadresse der API, z. B. "https://api.scaleway.ai/v1"
     * @param apiKey      der geheime Schlüssel
     * @param model       Name des Chat-Modells, z. B. "mistral-small-3.2-24b-instruct-2506"
     * @param temperature "Kreativität" von 0 bis etwa 1
     * @param maxTokens   höchstens so viele Tokens in der Antwort
     * @param retryDelay  Pause vor dem ersten Wiederholungsversuch
     */
    public OpenAiCompatibleChatModel(String baseUrl, String apiKey, String model, double temperature, int maxTokens,
            Duration retryDelay) {
        this.client = new OpenAiCompatibleClient(baseUrl, apiKey, retryDelay);
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /** Wie der Konstruktor, aber der API-Key kommt aus einer Umgebungsvariable. */
    public static OpenAiCompatibleChatModel fromEnvironment(String baseUrl, String apiKeyVariable, String model,
            double temperature, int maxTokens) {
        return new OpenAiCompatibleChatModel(baseUrl, OpenAiCompatibleClient.apiKeyFromEnvironment(apiKeyVariable),
                model, temperature, maxTokens, OpenAiCompatibleClient.DEFAULT_RETRY_DELAY);
    }

    /**
     * Schickt {"model": "...", "messages": [{"role": "system", "content": "..."}, ...],
     * "temperature": 0.2, "max_tokens": 800} und liest choices[0].message.content.
     */
    @Override
    public String chat(List<ChatMessage> messages) {
        ObjectNode request = OpenAiCompatibleClient.JSON.createObjectNode();
        request.put("model", model);
        ArrayNode messageArray = request.putArray("messages");
        for (ChatMessage message : messages) {
            messageArray.addObject()
                    .put("role", message.role().name().toLowerCase(Locale.ROOT))
                    .put("content", message.content());
        }
        request.put("temperature", temperature);
        request.put("max_tokens", maxTokens);

        JsonNode choice = client.post("/chat/completions", request).path("choices").path(0);
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual()) {
            throw new IllegalStateException("Antwort enthält keinen Text: " + choice);
        }
        String text = content.asText();
        return "length".equals(choice.path("finish_reason").asText()) ? text + TRUNCATION_NOTE : text;
    }
}
