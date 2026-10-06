package com.zuluindustries.rag.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.Document;

class LinePatternCleanerTest {

    private final LinePatternCleaner cleaner = new LinePatternCleaner("Regel (\\d{1,2}|x)", "REGEL");

    @Test
    void removesOnlyLinesThatMatchCompletely() {
        Document page = new Document("p41",
                "REGEL\n3.3a Gewinner im Zählspiel\nSiehe Regel 3 für Details.\nRegel 3\nRegel x ");

        String cleaned = cleaner.clean(page).orElseThrow().text();

        // "Siehe Regel 3 für Details." bleibt, weil die Zeile nicht NUR aus "Regel 3" besteht.
        assertEquals("3.3a Gewinner im Zählspiel\nSiehe Regel 3 für Details.", cleaned);
    }

    @Test
    void returnsEmptyWhenNothingIsLeft() {
        Document page = new Document("p0", "REGEL\nRegel 3");

        assertTrue(cleaner.clean(page).isEmpty());
    }
}
