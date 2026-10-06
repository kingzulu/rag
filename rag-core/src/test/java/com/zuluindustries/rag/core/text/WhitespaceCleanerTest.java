package com.zuluindustries.rag.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.Document;

class WhitespaceCleanerTest {

    @Test
    void normalizesTabsSpacesAndEmptyLines() {
        Document page = new Document("p41", " 3.3 \t Zählspiel \n3.3a  Gewinner\n\n\n\n\t• jeder Spieler ");

        String cleaned = new WhitespaceCleaner().clean(page).orElseThrow().text();

        assertEquals("3.3 Zählspiel\n3.3a Gewinner\n\n• jeder Spieler", cleaned);
    }
}
