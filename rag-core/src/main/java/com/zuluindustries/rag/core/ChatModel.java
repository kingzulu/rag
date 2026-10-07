package com.zuluindustries.rag.core;

import java.util.List;

/**
 * Ein Sprachmodell, das auf ein Gespräch (eine Folge von Nachrichten) mit
 * einer Antwort reagiert.
 */
public interface ChatModel {

    /**
     * @param messages das bisherige Gespräch, meist eine System- und eine Nutzernachricht
     * @return die Antwort des Modells
     */
    String chat(List<ChatMessage> messages);
}
