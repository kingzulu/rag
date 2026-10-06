package com.zuluindustries.rag.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.Document;

class TermChunkerTest {

    @Test
    void createsOneChunkPerTerm() {
        Document page = new Document("golfregeln#seite-242", """
                Abschlag
                Der Bereich, von dem der Spieler spielen muss.
                Aus
                Ist der Bereich außerhalb der Platzgrenzen.
                Alle Bereiche innerhalb sind kein Aus.
                Bewegliches Hemmnis
                Ein Hemmnis, das bewegt werden kann.""", Map.of("seite", "242"));

        List<Document> chunks = new TermChunker("Definitionen", 2000, 45, 6).chunk(List.of(page));

        assertEquals(List.of("Abschlag", "Aus", "Bewegliches Hemmnis"),
                chunks.stream().map(chunk -> chunk.metadata().get("nummer")).toList());
        assertEquals("Definitionen › Aus\n\nIst der Bereich außerhalb der Platzgrenzen.\n"
                + "Alle Bereiche innerhalb sind kein Aus.", chunks.get(1).text());
    }
}
