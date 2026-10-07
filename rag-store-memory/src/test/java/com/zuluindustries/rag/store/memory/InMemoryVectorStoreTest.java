package com.zuluindustries.rag.store.memory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.SearchResult;

class InMemoryVectorStoreTest {

    private final InMemoryVectorStore store = new InMemoryVectorStore("test-modell");

    private final Document bunker = new Document("bunker", "Regel 12 – Bunker", Map.of("nummer", "12", "seite", "114"));
    private final Document green = new Document("gruen", "Regel 13 – Grüns", Map.of("nummer", "13"));
    private final Document clubs = new Document("schlaeger", "Regel 4 – Ausrüstung", Map.of("nummer", "4"));

    @Test
    void returnsMostSimilarDocumentsFirst() {
        store.add(bunker, new float[] { 1, 0, 0 });
        store.add(green, new float[] { 0.7f, 0.7f, 0 });
        store.add(clubs, new float[] { 0, 0, 1 });

        List<SearchResult> results = store.search(new float[] { 1, 0.1f, 0 }, 2);

        assertEquals(List.of("bunker", "gruen"), results.stream().map(result -> result.document().id()).toList());
        assertTrue(results.get(0).score() > results.get(1).score());
    }

    @Test
    void searchesOnlyDocumentsMatchingFilter() {
        store.add(bunker, new float[] { 1, 0, 0 });
        store.add(green, new float[] { 0.7f, 0.7f, 0 });
        store.add(clubs, new float[] { 0, 0, 1 });

        // Ohne Filter wäre "bunker" der beste Treffer – er ist aber ausgeschlossen.
        List<SearchResult> results = store.search(new float[] { 1, 0, 0 }, 5,
                document -> !document.id().equals("bunker"));

        assertEquals(List.of("gruen", "schlaeger"), results.stream().map(result -> result.document().id()).toList());
    }

    @Test
    void replacesEntryWithSameId() {
        store.add(bunker, new float[] { 1, 0, 0 });
        store.add(new Document("bunker", "Neuer Text"), new float[] { 0, 1, 0 });

        assertEquals(1, store.size());
        assertEquals("Neuer Text", store.search(new float[] { 0, 1, 0 }, 1).getFirst().document().text());
    }

    @Test
    void knowsVectorOnlyForUnchangedText() {
        store.add(bunker, new float[] { 1, 0, 0 });

        assertTrue(store.vectorFor(bunker).isPresent());
        assertTrue(store.vectorFor(new Document("bunker", "Geänderter Text")).isEmpty());
        assertTrue(store.vectorFor(green).isEmpty());
    }

    @Test
    void removesEntriesNotInList() {
        store.add(bunker, new float[] { 1, 0, 0 });
        store.add(green, new float[] { 0, 1, 0 });

        assertEquals(1, store.retainAll(List.of("bunker")));
        assertEquals(1, store.size());
    }

    @Test
    void returnsDocumentsInInsertionOrder() {
        store.add(green, new float[] { 0, 1, 0 });
        store.add(bunker, new float[] { 1, 0, 0 });
        store.add(clubs, new float[] { 0, 0, 1 });

        assertEquals(List.of(green, bunker, clubs), store.documents());
    }

    @Test
    void rejectsVectorWithDifferentLength() {
        store.add(bunker, new float[] { 1, 0, 0 });

        assertThrows(IllegalArgumentException.class, () -> store.add(green, new float[] { 1, 0 }));
    }

    @Test
    void savesAndLoadsEverything(@TempDir Path tempDir) throws IOException {
        store.add(bunker, new float[] { 0.25f, -0.5f, 0.125f });
        store.add(green, new float[] { 0.1f, 0.2f, 0.3f });
        Path file = tempDir.resolve("index.jsonl");

        store.save(file);
        InMemoryVectorStore loaded = InMemoryVectorStore.load(file);

        assertEquals("test-modell", loaded.model());
        assertEquals(2, loaded.size());
        assertEquals(3, loaded.dimensions());
        assertArrayEquals(new float[] { 0.25f, -0.5f, 0.125f }, loaded.vectorFor(bunker).orElseThrow());
        Document loadedBunker = loaded.search(new float[] { 0.25f, -0.5f, 0.125f }, 1).getFirst().document();
        assertEquals(bunker, loadedBunker);   // id, Text und Metadaten (inkl. Umlaute) gleich
    }
}
