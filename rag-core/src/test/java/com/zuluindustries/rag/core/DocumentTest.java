package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class DocumentTest {

    @Test
    void createsDocumentWithMetadata() {
        Document doc = new Document("golfregeln-2023#seite-41", "3.3a Gewinner im Zählspiel",
                Map.of("seite", "41"));

        assertEquals("golfregeln-2023#seite-41", doc.id());
        assertEquals("3.3a Gewinner im Zählspiel", doc.text());
        assertEquals("41", doc.metadata().get("seite"));
    }

    @Test
    void rejectsBlankText() {
        assertThrows(IllegalArgumentException.class, () -> new Document("leer", "   "));
    }
}
