package com.zuluindustries.rag.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.Document;

class LineTest {

    @Test
    void remembersPageAndPositionOfEachLine() {
        Document page1 = new Document("p1", "Erste Zeile\nZweite Zeile");
        Document page2 = new Document("p2", "Dritte Zeile");

        List<Line> lines = Line.fromPages(List.of(page1, page2));

        assertEquals(3, lines.size());
        assertEquals(new Line("Zweite Zeile", page1, 1), lines.get(1));
        assertEquals(new Line("Dritte Zeile", page2, 0), lines.get(2));
    }

    @Test
    void joinsWordHyphenatedAcrossPageBreak() {
        Document page1 = new Document("p1", "Mit Ausnahme der Erleichterung „auf der Linie zu-");
        Document page2 = new Document("p2", "rück“ gilt Folgendes.\nNächste Zeile");

        List<Line> lines = Line.fromPages(List.of(page1, page2));

        assertEquals(2, lines.size());
        assertEquals("Mit Ausnahme der Erleichterung „auf der Linie zurück“ gilt Folgendes.", lines.get(0).text());
        assertEquals(page1, lines.get(0).page());
    }

    @Test
    void keepsHyphenBeforeCapitalLetterOnNextPage() {
        Document page1 = new Document("p1", "Brutto- oder Netto-");
        Document page2 = new Document("p2", "Ergebnisse");

        assertEquals(2, Line.fromPages(List.of(page1, page2)).size());
    }
}
