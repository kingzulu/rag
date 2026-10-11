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
 *
 * <p>Nicht jeder Anbieter versteht die Parameter gleich: Neuere OpenAI-Modelle erwarten
 * {@value #MAX_COMPLETION_TOKENS} statt {@value #MAX_TOKENS}, und Modelle mit eingebautem
 * "Nachdenken" (Reasoning) erlauben oft nur ihre Standard-Temperatur – dann wird keine
 * geschickt ({@code temperature = null}).
 */
public class OpenAiCompatibleChatModel implements ChatModel {

    /** Name des Token-Limits bei Scaleway, Mistral und älteren OpenAI-Modellen. */
    public static final String MAX_TOKENS = "max_tokens";
    /** Name des Token-Limits bei neueren OpenAI-Modellen (zählt auch die Denk-Tokens mit). */
    public static final String MAX_COMPLETION_TOKENS = "max_completion_tokens";

    static final String TRUNCATION_NOTE = "\n\n[Hinweis: Die Antwort wurde wegen der Längenbegrenzung abgeschnitten.]";

    private final OpenAiCompatibleClient client;
    private final String model;
    private final Double temperature;
    private final int maxTokens;
    private final String maxTokensField;

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
        this(baseUrl, apiKey, model, Double.valueOf(temperature), maxTokens, MAX_TOKENS, retryDelay);
    }

    /**
     * @param temperature    "Kreativität"; {@code null} = keine schicken (Standard des Modells)
     * @param maxTokensField Name des Token-Limits: {@value #MAX_TOKENS} oder {@value #MAX_COMPLETION_TOKENS}
     */
    public OpenAiCompatibleChatModel(String baseUrl, String apiKey, String model, Double temperature, int maxTokens,
            String maxTokensField, Duration retryDelay) {
        this.client = new OpenAiCompatibleClient(baseUrl, apiKey, retryDelay);
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.maxTokensField = maxTokensField;
    }

    /** Wie der Konstruktor, aber der API-Key kommt aus einer Umgebungsvariable. */
    public static OpenAiCompatibleChatModel fromEnvironment(String baseUrl, String apiKeyVariable, String model,
            double temperature, int maxTokens) {
        return fromEnvironment(baseUrl, apiKeyVariable, model, Double.valueOf(temperature), maxTokens, MAX_TOKENS);
    }

    /** Wie der Konstruktor mit Feldname, aber der API-Key kommt aus einer Umgebungsvariable. */
    public static OpenAiCompatibleChatModel fromEnvironment(String baseUrl, String apiKeyVariable, String model,
            Double temperature, int maxTokens, String maxTokensField) {
        return new OpenAiCompatibleChatModel(baseUrl, OpenAiCompatibleClient.apiKeyFromEnvironment(apiKeyVariable),
                model, temperature, maxTokens, maxTokensField, OpenAiCompatibleClient.DEFAULT_RETRY_DELAY);
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
        if (temperature != null) {
            request.put("temperature", temperature);
        }
        request.put(maxTokensField, maxTokens);

        JsonNode choice = client.post("/chat/completions", request).path("choices").path(0);
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual()) {
            throw new IllegalStateException("Antwort enthält keinen Text: " + choice);
        }
        String text = content.asText();
        return "length".equals(choice.path("finish_reason").asText()) ? text + TRUNCATION_NOTE : text;
    }
}
