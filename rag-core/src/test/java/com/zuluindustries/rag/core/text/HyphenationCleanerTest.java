package com.zuluindustries.rag.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.Document;

class HyphenationCleanerTest {

    private final HyphenationCleaner cleaner = new HyphenationCleaner();

    private String clean(String text) {
        return cleaner.clean(new Document("test", text)).orElseThrow().text();
    }

    @Test
    void joinsSyllableBreak() {
        assertEquals("an die ursprüngliche Stelle zurückzulegen.",
                clean("an die ursprüngliche Stelle zurückzu-\nlegen."));
    }

    @Test
    void joinsSyllableBreakWithTrailingSpaceAndSharpS() {
        assertEquals("gegen eine Regel verstoßen hatte", clean("gegen eine Regel versto- \nßen hatte"));
    }

    @Test
    void keepsRealHyphenBeforeCapitalLetter() {
        assertEquals("Brutto- oder Netto-Ergebnisse", clean("Brutto- oder Netto-\nErgebnisse"));
    }

    @Test
    void keepsSuspensionHyphenBeforeUndOder() {
        assertEquals("Ein- und Ausgang", clean("Ein-\nund Ausgang"));
    }

    @Test
    void leavesNormalLineBreaksAlone() {
        assertEquals("Zweck der Regel:\nZählspiel", clean("Zweck der Regel:\nZählspiel"));
    }
}
