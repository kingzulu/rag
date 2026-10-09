package com.zuluindustries.rag.core;

import java.util.List;
import java.util.Optional;

/**
 * Lässt ein Sprachmodell die Frage vor der Suche in die Sprache des Dokuments
 * übersetzen. Was genau das Modell liefert, bestimmt die Systemanweisung, z. B.:
 * <ul>
 * <li>die Frage, umformuliert mit den Fachbegriffen des Dokuments, oder</li>
 * <li>einen kurzen Absatz im Stil des Dokuments, der die Frage beantworten könnte
 * ("HyDE" – Hypothetical Document Embeddings). Er dient nur der Suche und muss
 * nicht in jedem Detail stimmen.</li>
 * </ul>
 *
 * <p>Antwortet das Modell mit {@value #NOTHING}, hat die Frage nichts mit dem
 * Dokument zu tun: Das Ergebnis ist dann {@link Optional#empty()}. Damit wirkt
 * die Übersetzung zugleich als Filter für themenfremde Fragen.
 */
public class ChatQueryRewriter implements QueryRewriter {

    /** Antwort des Modells, wenn die Frage nichts mit dem Dokument zu tun hat. */
    public static final String NOTHING = "keine";

    private final ChatModel chatModel;
    private final String systemPrompt;

    public ChatQueryRewriter(ChatModel chatModel, String systemPrompt) {
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public Optional<String> rewrite(String question) {
        String answer = chatModel.chat(List.of(ChatMessage.system(systemPrompt), ChatMessage.user(question)))
                .strip();
        String withoutPunctuation = answer.replaceAll("[.!\"„“]", "").strip();
        boolean noMatch = answer.isEmpty() || withoutPunctuation.equalsIgnoreCase(NOTHING);
        return noMatch ? Optional.empty() : Optional.of(answer);
    }
}
