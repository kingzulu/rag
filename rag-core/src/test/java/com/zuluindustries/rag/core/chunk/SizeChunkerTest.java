package com.zuluindustries.rag.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.Document;

class SizeChunkerTest {

    @Test
    void combinesPagesIntoOneChunkWhenShortEnough() {
        Document page20 = new Document("golfregeln#seite-20", "Spezifische Regeln\nRegel 9.3 Ball durch Naturkräfte bewegt",
                Map.of("seite", "20"));
        Document page21 = new Document("golfregeln#seite-21", "Regel 10.2b Andere Hilfe", Map.of("seite", "21"));

        List<Document> chunks = new SizeChunker("Wesentliche Änderungen 2023", 2000).chunk(List.of(page20, page21));

        assertEquals(1, chunks.size());
        assertEquals("Wesentliche Änderungen 2023\n\nSpezifische Regeln\nRegel 9.3 Ball durch Naturkräfte bewegt\n"
                + "Regel 10.2b Andere Hilfe", chunks.getFirst().text());
        assertEquals("20", chunks.getFirst().metadata().get("seite"));
    }
}
