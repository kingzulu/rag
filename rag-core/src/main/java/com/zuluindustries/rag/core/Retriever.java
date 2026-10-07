package com.zuluindustries.rag.core;

import java.util.List;

/**
 * Sucht zu einer Frage die passendsten Dokumente: Frage in einen Vektor
 * umwandeln, dann im {@link VectorStore} die ähnlichsten Vektoren suchen.
 *
 * <p>Manche Embedding-Modelle erwarten, dass Suchanfragen (anders als die
 * gespeicherten Dokumente) mit einer Anweisung beginnen. Dafür gibt es den
 * {@code queryPrefix}; ohne Anweisung einfach "" übergeben.
 */
public class Retriever {

    private final EmbeddingModel model;
    private final VectorStore store;
    private final String queryPrefix;

    public Retriever(EmbeddingModel model, VectorStore store, String queryPrefix) {
        this.model = model;
        this.store = store;
        this.queryPrefix = queryPrefix;
    }

    public List<SearchResult> search(String question, int topK) {
        float[] queryVector = model.embed(List.of(queryPrefix + question)).getFirst();
        return store.search(queryVector, topK);
    }
}
