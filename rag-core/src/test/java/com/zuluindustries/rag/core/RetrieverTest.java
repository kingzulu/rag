package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class RetrieverTest {

    private final Fakes.WordCountEmbeddingModel model = new Fakes.WordCountEmbeddingModel();
    private final Fakes.MapVectorStore store = new Fakes.MapVectorStore();

    @Test
    void findsMostSimilarDocumentsFirst() {
        new Indexer(model, store).index(List.of(
                new Document("grün", "Regeln für das Grün"),
                new Document("bunker", "Regeln für den Bunker"),
                new Document("ball", "Ball im Spiel")));

        List<SearchResult> results = new Retriever(model, store, "").search("Was gilt im Bunker?", 2);

        assertEquals(2, results.size());
        assertEquals("bunker", results.get(0).document().id());
        assertEquals(1.0, results.get(0).score(), 1e-9);
    }

    @Test
    void addsPrefixToQuestionOnly() {
        new Indexer(model, store).index(List.of(new Document("bunker", "Bunker")));
        model.embeddedTexts.clear();

        new Retriever(model, store, "<query>").search("Bunker?", 1);

        assertEquals(List.of("<query>Bunker?"), model.embeddedTexts);
    }
}
