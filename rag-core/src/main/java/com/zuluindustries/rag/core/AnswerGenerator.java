package com.zuluindustries.rag.core;

import java.util.List;

import com.zuluindustries.rag.core.chunk.ChunkAssembler;

/**
 * Das "G" in RAG: beantwortet eine Frage, indem es passende Quellen sucht und
 * ein Sprachmodell bittet, die Antwort <b>nur</b> aus diesen Quellen zu formulieren.
 *
 * <p>Ablauf:
 * <ol>
 * <li>Mit der Originalfrage suchen und die Schwelle prüfen. Ist schon der beste
 * Treffer nicht ähnlich genug (unter {@link Settings#minScore()}), passt nichts im
 * Dokument zur Frage: Das Sprachmodell wird gar nicht erst gefragt – das spart
 * Kosten und schließt erfundene Antworten auf themenfremde Fragen aus.</li>
 * <li>Optional die Frage für die Suche umformulieren ({@link QueryRewriter}, z. B.
 * Fachbegriffe ergänzen) und mit dem Suchtext erneut suchen. Die Schwelle wird
 * bewusst vorher mit der Originalfrage geprüft: Ergänzte Fachbegriffe würden auch
 * themenfremde Fragen ähnlicher erscheinen lassen.</li>
 * <li>Treffer ergänzen ({@link ContextExpander}, z. B. Geschwister-Abschnitte).</li>
 * <li>Nummerierte Quellen + <b>Original</b>frage → {@link ChatModel} → Antwort.</li>
 * </ol>
 *
 * <p>Wie sich das Modell verhalten soll (Sprache, Quellenangaben, was bei fehlender
 * Antwort zu tun ist), steht in der Systemanweisung, die von außen kommt – so
 * bleibt die Klasse für jedes Thema nutzbar.
 */
public class AnswerGenerator {

    /**
     * Die Antwort und die Quellen (Quelle [1] = erstes Element).
     *
     * @param modelAsked {@code false}, wenn die Antwort wegen der Schwelle ohne Sprachmodell entstand
     * @param searchText der Text, mit dem die Quellen gesucht wurden (die Frage, ggf. umformuliert)
     */
    public record Answer(String text, List<SearchResult> sources, boolean modelAsked, String searchText) {
    }

    /**
     * Alle Einstellungen an einer Stelle ("Parameter-Objekt"), statt einer langen
     * Liste von Konstruktor-Parametern, in der man leicht zwei Werte vertauscht.
     *
     * @param systemPrompt Verhaltensregeln für das Modell
     * @param topK         wie viele Treffer die Suche liefert
     * @param expander     ergänzt die Treffer um weitere Quellen (z. B. Geschwister-Abschnitte)
     * @param minScore     Mindest-Ähnlichkeit des besten Treffers, damit das Modell gefragt wird
     * @param noAnswerText Antwort, wenn der beste Treffer unter {@code minScore} liegt
     * @param rewriter     formt die Frage für die Suche um (z. B. Fachbegriffe ergänzen)
     */
    public record Settings(String systemPrompt, int topK, ContextExpander expander, double minScore,
            String noAnswerText, QueryRewriter rewriter) {

        /** Ohne Ergänzung der Treffer, ohne Schwelle und ohne Umformulierung. */
        public static Settings of(String systemPrompt, int topK) {
            return new Settings(systemPrompt, topK, ContextExpander.NONE, Double.NEGATIVE_INFINITY,
                    "Keine passenden Quellen gefunden.", QueryRewriter.NONE);
        }

        public Settings withExpander(ContextExpander newExpander) {
            return new Settings(systemPrompt, topK, newExpander, minScore, noAnswerText, rewriter);
        }

        public Settings withMinScore(double newMinScore, String newNoAnswerText) {
            return new Settings(systemPrompt, topK, expander, newMinScore, newNoAnswerText, rewriter);
        }

        public Settings withRewriter(QueryRewriter newRewriter) {
            return new Settings(systemPrompt, topK, expander, minScore, noAnswerText, newRewriter);
        }
    }

    private final Retriever retriever;
    private final ChatModel chatModel;
    private final Settings settings;

    public AnswerGenerator(Retriever retriever, ChatModel chatModel, Settings settings) {
        this.retriever = retriever;
        this.chatModel = chatModel;
        this.settings = settings;
    }

    /** Kurzform ohne Ergänzung der Treffer und ohne Schwelle. */
    public AnswerGenerator(Retriever retriever, ChatModel chatModel, String systemPrompt, int topK) {
        this(retriever, chatModel, Settings.of(systemPrompt, topK));
    }

    /** Kurzform mit Ergänzung der Treffer, ohne Schwelle. */
    public AnswerGenerator(Retriever retriever, ChatModel chatModel, String systemPrompt, int topK,
            ContextExpander expander) {
        this(retriever, chatModel, Settings.of(systemPrompt, topK).withExpander(expander));
    }

    public Answer answer(String question) {
        // 1. Mit der Originalfrage suchen und die Schwelle prüfen
        List<SearchResult> hits = retriever.search(question, settings.topK());
        if (hits.isEmpty() || hits.getFirst().score() < settings.minScore()) {
            return new Answer(settings.noAnswerText(), hits, false, question);
        }

        // 2. Optional umformulieren und mit dem Suchtext erneut suchen
        String searchText = settings.rewriter().rewrite(question);
        if (!searchText.equals(question)) {
            hits = retriever.search(searchText, settings.topK());
        }

        // 3. Treffer ergänzen, 4. Originalfrage beantworten lassen
        List<SearchResult> sources = settings.expander().expand(hits);
        String text = chatModel.chat(List.of(
                ChatMessage.system(settings.systemPrompt()),
                ChatMessage.user(buildUserMessage(question, sources))));
        return new Answer(text, sources, true, searchText);
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
