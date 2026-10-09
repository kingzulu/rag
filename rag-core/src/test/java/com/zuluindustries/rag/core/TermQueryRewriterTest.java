package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.ChatMessage.Role;

class TermQueryRewriterTest {

    private static final List<String> VOCABULARY =
            List.of("Tierloch", "Ungewöhnliche Platzverhältnisse", "Bunker", "Penalty Area");

    /** Chat-Attrappe: merkt sich die Nachrichten und antwortet mit einem festen Text. */
    private static class FixedChatModel implements ChatModel {

        final List<ChatMessage> received = new ArrayList<>();
        private final String reply;

        FixedChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public String chat(List<ChatMessage> messages) {
            received.addAll(messages);
            return reply;
        }
    }

    @Test
    void appendsTermsFromVocabularyToQuestion() {
        FixedChatModel chat = new FixedChatModel("Tierloch, Ungewöhnliche Platzverhältnisse");

        String rewritten = new TermQueryRewriter(chat, VOCABULARY, 5).rewrite("Ball im Kaninchenloch?").orElseThrow();

        assertEquals("Ball im Kaninchenloch? (Tierloch, Ungewöhnliche Platzverhältnisse)", rewritten);
    }

    @Test
    void sendsVocabularyAndQuestionToModel() {
        FixedChatModel chat = new FixedChatModel("keine");

        new TermQueryRewriter(chat, VOCABULARY, 5).rewrite("Ball im Kaninchenloch?");

        assertEquals(Role.SYSTEM, chat.received.get(0).role());
        assertTrue(chat.received.get(0).content().contains("höchstens 5"));
        String userMessage = chat.received.get(1).content();
        assertTrue(userMessage.contains("Tierloch, Ungewöhnliche Platzverhältnisse, Bunker, Penalty Area"));
        assertTrue(userMessage.endsWith("Frage: Ball im Kaninchenloch?"));
    }

    @Test
    void dropsInventedTermsAndNormalizesSpelling() {
        // "Hasenbau" steht nicht in der Liste; "tierloch." wird zu "Tierloch"; Doppeltes nur einmal
        FixedChatModel chat = new FixedChatModel("„Hasenbau“, tierloch., Tierloch\nBunker");

        List<String> terms = new TermQueryRewriter(chat, VOCABULARY, 5).terms("Frage?");

        assertEquals(List.of("Tierloch", "Bunker"), terms);
    }

    @Test
    void respectsMaximumNumberOfTerms() {
        FixedChatModel chat = new FixedChatModel("Tierloch, Bunker, Penalty Area");

        assertEquals(List.of("Tierloch", "Bunker"), new TermQueryRewriter(chat, VOCABULARY, 2).terms("Frage?"));
    }

    @Test
    void leavesQuestionUnchangedWhenNoTermFits() {
        FixedChatModel chat = new FixedChatModel("keine");

        // Keine Begriffe heißt hier nur "nichts zu ergänzen" – gesucht wird trotzdem, mit der Frage selbst.
        assertEquals(Optional.of("Wie koche ich Spaghetti?"),
                new TermQueryRewriter(chat, VOCABULARY, 5).rewrite("Wie koche ich Spaghetti?"));
    }
}
