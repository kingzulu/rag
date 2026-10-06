package com.zuluindustries.rag.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.Document;

class HeadingChunkerTest {

    private final HeadingChunker chunker = new HeadingChunker("Regeln", 2000, "Regel",
            Map.of(1, "Das Spiel", 2, "Der Platz", 3, "Das Turnier"), "Zweck der Regel:", 6);

    private static Document page(int number, String text) {
        return new Document("golfregeln#seite-" + number, text, Map.of("seite", String.valueOf(number)));
    }

    private static List<String> headings(List<Document> chunks) {
        return chunks.stream().map(chunk -> chunk.metadata().get("ueberschrift")).toList();
    }

    @Test
    void splitsAtHeadingsAndBuildsHeadingChain() {
        Document page26 = page(26, """
                1 Das Spiel, Verhalten der Spieler
                und die Regeln
                Zweck der Regel:
                Regel 1 schreibt dem Spieler die Grundsätze vor.
                1.1 Das Golfspiel
                Golf wird über eine Runde gespielt.
                1.2 Richtlinien für das Verhalten
                1.2a Von allen Spielern erwartetes Verhalten
                Alle Spieler handeln aufrichtig, wie es Regel
                14.2d verlangt.
                1.1 Siehe oben.""");
        Document page31 = page(31, """
                2 Der Platz
                Zweck der Regel:
                Regel 2 stellt die Grundbegriffe vor.
                2.1 Platzgrenzen und das Aus
                Golf wird auf einem Platz gespielt.""");

        List<Document> chunks = chunker.chunk(List.of(page26, page31));

        assertEquals(List.of(
                "Regel 1 – Das Spiel › Zweck der Regel",
                "Regel 1 – Das Spiel › 1.1 Das Golfspiel",
                // "1.2" hat keinen eigenen Text und ergibt daher keinen Chunk
                "Regel 1 – Das Spiel › 1.2 Richtlinien für das Verhalten › 1.2a Von allen Spielern erwartetes Verhalten",
                "Regel 2 – Der Platz › Zweck der Regel",
                "Regel 2 – Der Platz › 2.1 Platzgrenzen und das Aus"), headings(chunks));

        // Zerstückelter Titel ("1 Das Spiel, …") und Markierung sind verworfen.
        assertEquals("Regel 1 – Das Spiel › Zweck der Regel\n\nRegel 1 schreibt dem Spieler die Grundsätze vor.",
                chunks.get(0).text());
        // "14.2d verlangt." (klein) und "1.1 Siehe oben." (Nummer nicht aufsteigend) sind keine Überschriften.
        assertEquals(List.of("Alle Spieler handeln aufrichtig, wie es Regel", "14.2d verlangt.", "1.1 Siehe oben."),
                chunks.get(2).text().lines().skip(2).toList());
        // Der Titel von Regel 2 ("2 Der Platz") gehört nicht mehr zum letzten Chunk von Regel 1.
        assertEquals("1.2a", chunks.get(2).metadata().get("nummer"));
        assertEquals("2.1", chunks.get(4).metadata().get("nummer"));
        assertEquals("31", chunks.get(4).metadata().get("seite"));
    }

    @Test
    void treatsMarkerAfterSubHeadingAsNormalText() {
        Document page34 = page(34, """
                Zweck der Regel:
                Regel 3 behandelt die Spielformen.
                3.1 Wesentliche Bestandteile""");
        Document page41 = page(41, """
                3.3 Zählspiel
                Zweck der Regel:
                Zählspiel hat besondere Regeln.""");

        List<Document> chunks = chunker.chunk(List.of(page34, page41));

        assertEquals(List.of("Regel 3 – Das Turnier › Zweck der Regel", "Regel 3 – Das Turnier › 3.3 Zählspiel"),
                headings(chunks));
        assertEquals("Regel 3 – Das Turnier › 3.3 Zählspiel\n\nZweck der Regel:\nZählspiel hat besondere Regeln.",
                chunks.get(1).text());
    }
}
