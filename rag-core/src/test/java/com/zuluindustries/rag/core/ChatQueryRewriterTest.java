package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.ChatMessage.Role;

class ChatQueryRewriterTest {

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
    void returnsTextFromModelAsSearchText() {
        FixedChatModel chat = new FixedChatModel("  Ball in einer gelben Penalty Area: Welche Erleichterung? \n");

        String searchText = new ChatQueryRewriter(chat, "Übersetze in Regelsprache.")
                .rewrite("ins wasser geschlagen, gelb markiert").orElseThrow();

        assertEquals("Ball in einer gelben Penalty Area: Welche Erleichterung?", searchText);
        assertEquals(new ChatMessage(Role.SYSTEM, "Übersetze in Regelsprache."), chat.received.get(0));
        assertEquals(new ChatMessage(Role.USER, "ins wasser geschlagen, gelb markiert"), chat.received.get(1));
    }

    @Test
    void signalsOffTopicQuestionWhenModelSaysNothingFits() {
        for (String reply : List.of("keine", "Keine.", "„keine“", "")) {
            Optional<String> searchText = new ChatQueryRewriter(new FixedChatModel(reply), "System")
                    .rewrite("Wie koche ich Spaghetti?");

            assertTrue(searchText.isEmpty(), "Antwort: " + reply);
        }
    }
}
