package com.zuluindustries.rag.app;

import com.zuluindustries.rag.core.EmbeddingModel;
import com.zuluindustries.rag.embedding.openai.OpenAiCompatibleEmbeddingModel;

/**
 * Einstellungen für die Embeddings über Scaleway – an einer Stelle, damit
 * alle Programme dasselbe Modell verwenden.
 */
public final class Scaleway {

    public static final String BASE_URL = "https://api.scaleway.ai/v1";
    public static final String MODEL = "bge-multilingual-gemma2";
    public static final String API_KEY_VARIABLE = "SCW_SECRET_KEY";

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
}
