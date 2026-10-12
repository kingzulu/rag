package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class FusionSearcherTest {

    private static Retriever retriever(Fakes.WordCountEmbeddingModel embeddings, Fakes.MapVectorStore store) {
        return new Retriever(embeddings, store, "");
    }

    @Test
    void combinesHitsOfQuestionAndSearchTextAndScoresBySearchText() {
        Fakes.WordCountEmbeddingModel embeddings = new Fakes.WordCountEmbeddingModel();
        Fakes.MapVectorStore store = new Fakes.MapVectorStore();
        new Indexer(embeddings, store).index(List.of(
                new Document("bunker", "Bunker"), new Document("gruen", "Grün"), new Document("ball", "Ball")));
        // Je Einzelsuche nur der beste Treffer: die Frage findet "gruen", der Suchtext "bunker".
        FusionSearcher searcher = new FusionSearcher(retriever(embeddings, store), retriever(embeddings, store), 1, 60);

        List<SearchResult> results = searcher.search("Grün?", "Bunker Bunker Ball", 2);

        assertEquals(List.of("gruen", "bunker"), results.stream().map(r -> r.document().id()).toList());
        assertEquals(0.0, results.get(0).score(), 1e-6);                  // Ähnlichkeit zum Suchtext, nicht RRF
        assertEquals(2 / Math.sqrt(5), results.get(1).score(), 1e-6);
    }

    @Test
    void simpleSearcherUsesOnlySearchText() {
        Fakes.WordCountEmbeddingModel embeddings = new Fakes.WordCountEmbeddingModel();
        Fakes.MapVectorStore store = new Fakes.MapVectorStore();
        new Indexer(embeddings, store).index(List.of(new Document("bunker", "Bunker"), new Document("gruen", "Grün")));

        List<SearchResult> results = Searcher.of(retriever(embeddings, store)).search("Grün?", "Bunker", 1);

        assertEquals("bunker", results.getFirst().document().id());
    }
}
