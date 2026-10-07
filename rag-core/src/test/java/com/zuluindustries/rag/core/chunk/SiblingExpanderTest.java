package com.zuluindustries.rag.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.SearchResult;

class SiblingExpanderTest {

    private static Document chunk(String id, String number) {
        return new Document(id, "Text von " + id, Map.of("nummer", number));
    }

    /** Alle Chunks in Buch-Reihenfolge. */
    private final List<Document> book = List.of(
            chunk("r19-zweck", "19"),
            chunk("r19-2a", "19.2a"),
            chunk("r19-2b", "19.2b"),
            chunk("r19-2c-teil1", "19.2c"),
            chunk("r19-2c-teil2", "19.2c"),
            chunk("r19-3a", "19.3a"),
            chunk("r19-20", "19.20"),   // gibt es nicht wirklich – prüft die Abgrenzung von "19.2"
            chunk("def-bunker", "Bunker"));

    private static List<String> ids(List<SearchResult> results) {
        return results.stream().map(result -> result.document().id()).toList();
    }

    @Test
    void addsSiblingsOfSameSubsectionInBookOrder() {
        List<SearchResult> hits = List.of(
                new SearchResult(book.get(3), 0.37),    // 19.2c Teil 1
                new SearchResult(book.get(7), 0.30));   // Definition Bunker

        List<SearchResult> expanded = new SiblingExpander(book, 10, 100_000).expand(hits);

        assertEquals(List.of("r19-2a", "r19-2b", "r19-2c-teil1", "r19-2c-teil2", "def-bunker"), ids(expanded));
        assertEquals(0.37, expanded.get(2).score());            // Treffer behalten ihre Ähnlichkeit
        assertTrue(Double.isNaN(expanded.get(0).score()));      // ergänzte Chunks haben NaN
    }

    @Test
    void respectsMaximumNumberOfChunks() {
        List<SearchResult> hits = List.of(
                new SearchResult(book.get(3), 0.37),
                new SearchResult(book.get(7), 0.30));

        List<SearchResult> expanded = new SiblingExpander(book, 3, 100_000).expand(hits);

        assertEquals(List.of("r19-2a", "r19-2c-teil1", "def-bunker"), ids(expanded));
    }

    @Test
    void neverRemovesHitsEvenIfCharacterLimitIsExceeded() {
        List<SearchResult> hits = List.of(new SearchResult(book.get(3), 0.37));

        List<SearchResult> expanded = new SiblingExpander(book, 10, 10).expand(hits);

        assertEquals(List.of("r19-2c-teil1"), ids(expanded));
    }

    @Test
    void leavesDefinitionsAndChaptersAlone() {
        List<SearchResult> hits = List.of(
                new SearchResult(book.get(0), 0.40),    // "19" (Zweck der Regel)
                new SearchResult(book.get(7), 0.30));   // "Bunker"

        List<SearchResult> expanded = new SiblingExpander(book, 10, 100_000).expand(hits);

        assertEquals(List.of("r19-zweck", "def-bunker"), ids(expanded));
    }
}
