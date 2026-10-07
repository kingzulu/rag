package com.zuluindustries.rag.core;

import java.util.List;

/**
 * Bringt einen {@link VectorStore} auf den Stand einer Liste von Dokumenten.
 * Eingebettet werden nur Dokumente, die neu sind oder deren Text sich geändert
 * hat – alle anderen behalten ihren gespeicherten Vektor. Dokumente, die es
 * nicht mehr gibt, werden entfernt.
 */
public class Indexer {

    /** Was beim Indexieren passiert ist. */
    public record Result(int reused, int embedded, int removed) {
    }

    private final EmbeddingModel model;
    private final VectorStore store;

    public Indexer(EmbeddingModel model, VectorStore store) {
        this.model = model;
        this.store = store;
    }

    public Result index(List<Document> documents) {
        int removed = store.retainAll(documents.stream().map(Document::id).toList());

        List<Document> missing = documents.stream()
                .filter(document -> store.vectorFor(document).isEmpty())
                .toList();
        if (!missing.isEmpty()) {
            List<float[]> vectors = model.embed(missing.stream().map(Document::text).toList());
            for (int i = 0; i < missing.size(); i++) {
                store.add(missing.get(i), vectors.get(i));
            }
        }
        return new Result(documents.size() - missing.size(), missing.size(), removed);
    }
}
