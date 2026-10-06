package com.zuluindustries.rag.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.Document;

class ChunkAssemblerTest {

    private final Document page = new Document("golfregeln#seite-41", "egal",
            Map.of("quelle", "golfregeln.pdf", "seite", "41", "seite_gedruckt", "39"));

    private List<Line> lines(String... texts) {
        return Arrays.stream(texts).map(text -> new Line(text, page, 0)).toList();
    }

    @Test
    void createsChunkWithHeadingAndMetadata() {
        ChunkAssembler assembler = new ChunkAssembler("Regeln", 2000);

        assembler.add("Regel 3 – Das Turnier › 3.3 Zählspiel", "3.3", lines("Zählspiel hat besondere Regeln."));

        Document chunk = assembler.build().getFirst();
        assertEquals("golfregeln#regeln-1", chunk.id());
        assertEquals("Regel 3 – Das Turnier › 3.3 Zählspiel\n\nZählspiel hat besondere Regeln.", chunk.text());
        assertEquals("Regeln", chunk.metadata().get("abschnitt"));
        assertEquals("3.3", chunk.metadata().get("nummer"));
        assertEquals("41", chunk.metadata().get("seite"));
        assertEquals("39", chunk.metadata().get("seite_gedruckt"));
        assertEquals("golfregeln.pdf", chunk.metadata().get("quelle"));
    }

    @Test
    void ignoresEmptySections() {
        ChunkAssembler assembler = new ChunkAssembler("Regeln", 2000);

        assembler.add("1.2 Richtlinien", "1.2", lines("", " "));

        assertTrue(assembler.build().isEmpty());
    }

    @Test
    void splitsLongSectionsAndRepeatsHeading() {
        ChunkAssembler assembler = new ChunkAssembler("Regeln", 60);

        assembler.add("3.3b Ergebnisse", "3.3b", lines(
                "Die Ergebnisse werden eingetragen.",      // 35 Zeichen
                "Der Zähler wird bestimmt.",               // 26 Zeichen -> zusammen über 60
                "Der Spieler muss denselben Zähler haben."));

        List<Document> chunks = assembler.build();
        assertEquals(3, chunks.size());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.text().startsWith("3.3b Ergebnisse\n\n")));
        assertEquals("golfregeln#regeln-3", chunks.get(2).id());
    }

    @Test
    void prefersBreakBeforeNumberedParagraph() {
        ChunkAssembler assembler = new ChunkAssembler("Regeln", 80);

        assembler.add("3.3b Ergebnisse", "3.3b", lines(
                "Einleitender Satz zur Regel über die Ergebnisse.",   // 49 Zeichen, mehr als die Hälfte
                "(1) Verantwortung des Zählers.",
                "Text zu (1)."));

        List<Document> chunks = assembler.build();
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(1).text().contains("(1) Verantwortung des Zählers.\nText zu (1)."));
    }

    @Test
    void createsSlugFromSectionName() {
        assertEquals("wesentliche-aenderungen-2023", ChunkAssembler.slug("Wesentliche Änderungen 2023"));
    }
}
