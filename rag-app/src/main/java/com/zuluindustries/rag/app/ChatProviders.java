package com.zuluindustries.rag.app;

import java.util.Locale;

import com.zuluindustries.rag.core.ChatModel;
import com.zuluindustries.rag.provider.openai.OpenAiCompatibleChatModel;

/**
 * Antwortmodelle verschiedener Anbieter – für AskApp und zum Vergleichen in der
 * AnswerEvaluation. Getauscht wird nur das Modell, das die Antwort aus den
 * Quellen formuliert; Übersetzung (HyDE) und Embeddings bleiben bei Scaleway,
 * denn der Suchindex ist mit dem Scaleway-Embedding-Modell berechnet.
 *
 * <p>Jeder Anbieter braucht seinen API-Key als Umgebungsvariable in der Run
 * Configuration – nie im Code.
 *
 * <p>Vergleich 10.10.2026 (AnswerEvaluation, je 2–3 Läufe): Mistral Small verweigert
 * Fragen, deren Antwort aus den Quellen gefolgert werden muss ("am Gebüschrand
 * droppen?", "Ball beim Aufteen berührt – Schlag?"); gpt-6.1-sol beantwortet sie
 * richtig und brauchte keine Nachbesserung. Kosten etwa 0,8 Cent pro Antwort.
 */
final class ChatProviders {

    /** Ein Antwortmodell und wie es in der Ausgabe heißt. */
    record Choice(String description, ChatModel model) {
    }

    static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    static final String OPENAI_API_KEY_VARIABLE = "OPENAI_API_KEY";
    static final String OPENAI_MODEL = "gpt-6.1-sol";
    /** Großzügig, weil bei OpenAI auch die unsichtbaren Denk-Tokens zählen; bezahlt wird nur der Verbrauch. */
    static final int OPENAI_MAX_TOKENS = 4000;

    /** Standard für AskApp – siehe Vergleich oben. */
    static final String DEFAULT_FOR_ANSWERS = "openai";

    private ChatProviders() {
    }

    /**
     * Für die AnswerEvaluation – Scaleway mit Temperatur 0, damit Läufe vergleichbar sind.
     *
     * @param name "scaleway" oder "openai" (Standard-Temperatur des Modells)
     */
    static Choice forEvaluation(String name) {
        return choose(name, 0.0);
    }

    /** Für AskApp – Scaleway mit der normalen Temperatur {@link Scaleway#CHAT_TEMPERATURE}. */
    static Choice forAnswers(String name) {
        return choose(name, Scaleway.CHAT_TEMPERATURE);
    }

    private static Choice choose(String name, double scalewayTemperature) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "scaleway" -> new Choice("Scaleway " + Scaleway.CHAT_MODEL + ", Temperatur " + scalewayTemperature,
                    Scaleway.chatModel(scalewayTemperature));
            case "openai" -> new Choice("OpenAI " + OPENAI_MODEL + ", Standard-Temperatur", openAi());
            default -> throw new IllegalArgumentException(
                    "Unbekannter Anbieter \"" + name + "\" – erlaubt: scaleway, openai");
        };
    }

    /** Neuere OpenAI-Modelle: "max_completion_tokens" statt "max_tokens", keine eigene Temperatur. */
    static ChatModel openAi() {
        return OpenAiCompatibleChatModel.fromEnvironment(OPENAI_BASE_URL, OPENAI_API_KEY_VARIABLE, OPENAI_MODEL,
                null, OPENAI_MAX_TOKENS, OpenAiCompatibleChatModel.MAX_COMPLETION_TOKENS);
    }
}
