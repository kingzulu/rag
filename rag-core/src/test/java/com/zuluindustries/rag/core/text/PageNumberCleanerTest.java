package com.zuluindustries.rag.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.Document;

class PageNumberCleanerTest {

    private final PageNumberCleaner cleaner = new PageNumberCleaner(-2);

    @Test
    void removesPrintedPageNumberAndStoresItAsMetadata() {
        Document page = new Document("p41", "39\n3.3 Zählspiel\nZweck der Regel:", Map.of("seite", "41"));

        Document cleaned = cleaner.clean(page).orElseThrow();

        assertEquals("3.3 Zählspiel\nZweck der Regel:", cleaned.text());
        assertEquals("39", cleaned.metadata().get("seite_gedruckt"));
        assertEquals("41", cleaned.metadata().get("seite"));
    }

    @Test
    void keepsNumberThatIsNotThePageNumber() {
        // Auf der Titelseite von Regel 9 steht die große "9" – das ist keine Seitenzahl.
        Document page = new Document("p90", "Zweck der Regel:\n88\n9\nREGEL", Map.of("seite", "90"));

        Document cleaned = cleaner.clean(page).orElseThrow();

        assertEquals("Zweck der Regel:\n9\nREGEL", cleaned.text());
        assertEquals("88", cleaned.metadata().get("seite_gedruckt"));
    }

    @Test
    void findsPageNumberInTheMiddleIfNotAtStartOrEnd() {
        // Seite 42 mit Scorekarte: Durch die Abbildung steht die "40" mitten auf der Seite.
        Document page = new Document("p42",
                "Spielers für das Loch treffen.\nder Scorekarte bestätigt.\nund\n40\n6\n6\nLoch\nPar\nOut",
                Map.of("seite", "42"));

        Document cleaned = cleaner.clean(page).orElseThrow();

        assertEquals("Spielers für das Loch treffen.\nder Scorekarte bestätigt.\nund\n6\n6\nLoch\nPar\nOut",
                cleaned.text());
        assertEquals("40", cleaned.metadata().get("seite_gedruckt"));
    }

    @Test
    void prefersPageNumberAtStartOverSameNumberInTheMiddle() {
        Document page = new Document("p42", "40\nText\nText\nText\n40\nText\nText\nText",
                Map.of("seite", "42"));

        Document cleaned = cleaner.clean(page).orElseThrow();

        assertEquals("Text\nText\nText\n40\nText\nText\nText", cleaned.text());
    }

    @Test
    void leavesDocumentUnchangedWithoutPageNumber() {
        Document page = new Document("p1", "Offizielle Golfregeln", Map.of("seite", "1"));

        Document cleaned = cleaner.clean(page).orElseThrow();

        assertEquals(page, cleaned);
        assertNull(cleaned.metadata().get("seite_gedruckt"));
    }
}
