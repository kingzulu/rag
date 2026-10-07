package com.zuluindustries.rag.core;

import java.util.List;
import java.util.function.Predicate;

/**
 * Sucht zu einer Frage die passendsten Dokumente: Frage in einen Vektor
 * umwandeln, dann im {@link VectorStore} die ähnlichsten Vektoren suchen.
 *
 * <p>Die beiden Schritte gibt es auch einzeln ({@link #embed} und
 * {@link #search(float[], int, Predicate)}): So lässt sich ein Fragevektor für
 * mehrere Suchen verwenden, ohne die Frage mehrmals einzubetten.
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

    /** Frage einbetten und die {@code topK} ähnlichsten Dokumente suchen. */
    public List<SearchResult> search(String question, int topK) {
        return search(embed(question), topK, document -> true);
    }

    /** Wandelt die Frage (mit Anweisung) in einen Vektor um. */
    public float[] embed(String question) {
        return model.embed(List.of(queryPrefix + question)).getFirst();
    }

    /** Sucht mit einem schon berechneten Fragevektor, nur unter Dokumenten, die den Filter erfüllen. */
    public List<SearchResult> search(float[] questionVector, int topK, Predicate<Document> filter) {
        return store.search(questionVector, topK, filter);
    }
}
