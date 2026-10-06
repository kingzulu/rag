package com.zuluindustries.rag.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.Document;

class CompositeCleanerTest {

    private final CompositeCleaner cleaner = new CompositeCleaner(
            new PageNumberCleaner(-2),
            new LinePatternCleaner("Regel (\\d{1,2}|x)", "REGEL"),
            new HyphenationCleaner(),
            new WhitespaceCleaner());

    @Test
    void appliesAllCleanersInOrder() {
        Document page = new Document("p41",
                "39\n 3.3 \t Zählspiel\nZählspiel hat besondere Regeln (bezüglich des Ein-\nlochens)\nRegel 3\n",
                Map.of("seite", "41"));

        Document cleaned = cleaner.clean(page).orElseThrow();

        assertEquals("3.3 Zählspiel\nZählspiel hat besondere Regeln (bezüglich des Einlochens)", cleaned.text());
        assertEquals("39", cleaned.metadata().get("seite_gedruckt"));
    }

    @Test
    void dropsPageThatOnlyContainedLayoutLines() {
        Document page = new Document("p24", "22\nREGEL\nRegel 1", Map.of("seite", "24"));

        assertTrue(cleaner.clean(page).isEmpty());
    }
}
