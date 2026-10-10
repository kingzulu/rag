package com.zuluindustries.rag.core;

/**
 * Eine Nachricht in einem Gespräch mit einem Sprachmodell.
 *
 * @param role    wer spricht
 * @param content der Text der Nachricht
 */
public record ChatMessage(Role role, String content) {

    public enum Role {
        /** Anweisungen, wie sich das Modell verhalten soll */
        SYSTEM,
        /** Nachricht des Menschen (bei RAG: Quellen und Frage) */
        USER,
        /** Antwort des Modells */
        ASSISTANT
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }
}
