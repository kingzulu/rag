package com.zuluindustries.rag.core;

import java.util.List;

import com.zuluindustries.rag.core.chunk.ChunkAssembler;

/**
 * Das "G" in RAG: beantwortet eine Frage, indem es passende Quellen sucht und
 * ein Sprachmodell bittet, die Antwort <b>nur</b> aus diesen Quellen zu formulieren.
 *
 * <p>Ablauf: Frage → {@link Retriever} → nummerierte Quellen + Frage → {@link ChatModel} → Antwort.
 * Wie sich das Modell verhalten soll (Sprache, Quellenangaben, was bei fehlender
 * Antwort zu tun ist), steht in der Systemanweisung, die von außen kommt – so
 * bleibt die Klasse für jedes Thema nutzbar.
 */
public class AnswerGenerator {

    /** Die Antwort des Modells und die Quellen, die ihm vorlagen (Quelle [1] = erstes Element). */
    public record Answer(String text, List<SearchResult> sources) {
    }

    private final Retriever retriever;
    private final ChatModel chatModel;
    private final String systemPrompt;
    private final int topK;

    /**
     * @param systemPrompt Verhaltensregeln für das Modell
     * @param topK         wie viele Quellen das Modell bekommt
     */
    public AnswerGenerator(Retriever retriever, ChatModel chatModel, String systemPrompt, int topK) {
        this.retriever = retriever;
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;
        this.topK = topK;
    }

    public Answer answer(String question) {
        List<SearchResult> sources = retriever.search(question, topK);
        String text = chatModel.chat(List.of(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(buildUserMessage(question, sources))));
        return new Answer(text, sources);
    }

    /**
     * Baut die Nutzernachricht: alle Quellen mit Nummer und Fundstelle, danach die Frage.
     * <pre>
     * Quellen:
     *
     * [1] Regel 12 – Bunker › … › 12.2b Einschränkungen, den Bunkersand zu berühren
     *
     * Text des Chunks …
     * (Fundstelle: Seite 113)
     *
     * [2] …
     *
     * Frage: …
     * </pre>
     */
    static String buildUserMessage(String question, List<SearchResult> sources) {
        StringBuilder message = new StringBuilder("Quellen:\n\n");
        for (int i = 0; i < sources.size(); i++) {
            Document chunk = sources.get(i).document();
            message.append('[').append(i + 1).append("] ").append(chunk.text()).append('\n');
            String page = chunk.metadata().getOrDefault(ChunkAssembler.PRINTED_PAGE_KEY,
                    chunk.metadata().get(ChunkAssembler.PAGE_KEY));
            if (page != null) {
                message.append("(Fundstelle: Seite ").append(page).append(")\n");
            }
            message.append('\n');
        }
        message.append("Frage: ").append(question);
        return message.toString();
    }
}
