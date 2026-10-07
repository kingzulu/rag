package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class IndexerTest {

    private final Fakes.WordCountEmbeddingModel model = new Fakes.WordCountEmbeddingModel();
    private final Fakes.MapVectorStore store = new Fakes.MapVectorStore();
    private final Indexer indexer = new Indexer(model, store);

    @Test
    void embedsAllDocumentsTheFirstTime() {
        Indexer.Result result = indexer.index(List.of(new Document("a", "Bunker"), new Document("b", "Grün")));

        assertEquals(new Indexer.Result(0, 2, 0), result);
        assertEquals(2, store.size());
    }

    @Test
    void embedsOnlyNewOrChangedDocumentsTheSecondTime() {
        indexer.index(List.of(new Document("a", "Bunker"), new Document("b", "Grün"), new Document("c", "Ball")));
        model.embeddedTexts.clear();

        Indexer.Result result = indexer.index(List.of(
                new Document("a", "Bunker"),                // unverändert
                new Document("b", "Grün und Ball"),         // Text geändert
                new Document("d", "Bunker und Ball")));     // neu; "c" gibt es nicht mehr

        assertEquals(new Indexer.Result(1, 2, 1), result);
        assertEquals(List.of("Grün und Ball", "Bunker und Ball"), model.embeddedTexts);
        assertEquals(3, store.size());
    }

    @Test
    void doesNotCallModelWhenNothingChanged() {
        List<Document> documents = List.of(new Document("a", "Bunker"));
        indexer.index(documents);
        model.embeddedTexts.clear();

        indexer.index(documents);

        assertTrue(model.embeddedTexts.isEmpty());
    }
}
