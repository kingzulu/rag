package com.zuluindustries.rag.app;

import com.zuluindustries.rag.core.ChatModel;
import com.zuluindustries.rag.core.EmbeddingModel;
import com.zuluindustries.rag.provider.openai.OpenAiCompatibleChatModel;
import com.zuluindustries.rag.provider.openai.OpenAiCompatibleEmbeddingModel;

/**
 * Einstellungen für die Embeddings über Scaleway – an einer Stelle, damit
 * alle Programme dasselbe Modell verwenden.
 */
public final class Scaleway {

    public static final String BASE_URL = "https://api.scaleway.ai/v1";
    public static final String MODEL = "bge-multilingual-gemma2";
    public static final String API_KEY_VARIABLE = "SCW_SECRET_KEY";

    /** Chat-Modell für die Antworten; Mistral Small: günstig, gut auf Deutsch, von Scaleway empfohlen. */
    public static final String CHAT_MODEL = "mistral-small-3.2-24b-instruct-2506";
    /** Niedrig, weil Regelauskünfte genau und wiederholbar sein sollen. */
    public static final double CHAT_TEMPERATURE = 0.2;
    public static final int CHAT_MAX_TOKENS = 800;

    /**
     * Anweisung, die laut Modellbeschreibung von bge-multilingual-gemma2 vor jede
     * Suchanfrage gehört (nicht vor die gespeicherten Chunks).
     */
    public static final String QUERY_INSTRUCTION =
            "<instruct>Given a web search query, retrieve relevant passages that answer the query.\n<query>";

    private Scaleway() {
    }

    /** Das Embedding-Modell; der API-Key kommt aus der Umgebungsvariable {@value #API_KEY_VARIABLE}. */
    public static EmbeddingModel embeddingModel() {
        return OpenAiCompatibleEmbeddingModel.fromEnvironment(BASE_URL, API_KEY_VARIABLE, MODEL);
    }

    /** Das Chat-Modell; der API-Key kommt aus derselben Umgebungsvariable. */
    public static ChatModel chatModel() {
        return chatModel(CHAT_TEMPERATURE);
    }

    /** Das Chat-Modell mit eigener Temperatur – 0 für möglichst wiederholbare Antworten (Evaluation). */
    public static ChatModel chatModel(double temperature) {
        return OpenAiCompatibleChatModel.fromEnvironment(BASE_URL, API_KEY_VARIABLE, CHAT_MODEL,
                temperature, CHAT_MAX_TOKENS);
    }
}
