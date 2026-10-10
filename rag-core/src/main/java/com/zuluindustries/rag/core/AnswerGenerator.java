package com.zuluindustries.rag.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.zuluindustries.rag.core.chunk.ChunkAssembler;

/**
 * Das "G" in RAG: beantwortet eine Frage, indem es passende Quellen sucht und
 * ein Sprachmodell bittet, die Antwort <b>nur</b> aus diesen Quellen zu formulieren.
 *
 * <p>Ablauf:
 * <ol>
 * <li>Die Frage in die Sprache des Dokuments übersetzen ({@link QueryRewriter},
 * z. B. ein Absatz im Stil des Regelbuchs). Erkennt der Übersetzer, dass die Frage
 * nichts mit dem Dokument zu tun hat, ist hier Schluss.</li>
 * <li>Mit dem Suchtext suchen und die Schwelle prüfen. Ist schon der beste Treffer
 * nicht ähnlich genug (unter {@link Settings#minScore()}), passt nichts im Dokument
 * zur Frage – ein Sicherheitsnetz, falls der Übersetzer eine themenfremde Frage
 * nicht erkennt.</li>
 * <li>Treffer ergänzen ({@link ContextExpander}, z. B. Geschwister-Abschnitte).</li>
 * <li>Nummerierte Quellen + <b>Original</b>frage → {@link ChatModel} → Antwort.</li>
 * <li>Die Antwort prüfen ({@link AnswerCheck}, z. B. ob jede Regelnummer in einer
 * Quelle steht). Findet die Prüfung etwas, bekommt das Modell die Probleme gezeigt
 * und soll die Antwort <b>einmal</b> neu schreiben. Was danach noch auffällt, steht
 * in {@link Answer#problems()} – die Antwort wird trotzdem geliefert, mit Hinweis.</li>
 * </ol>
 * In den Fällen 1 und 2 wird das Antwort-Modell gar nicht erst gefragt – das spart
 * Kosten und schließt erfundene Antworten auf themenfremde Fragen aus.
 *
 * <p>Wie sich das Modell verhalten soll (Sprache, Quellenangaben, was bei fehlender
 * Antwort zu tun ist), steht in der Systemanweisung, die von außen kommt – so
 * bleibt die Klasse für jedes Thema nutzbar.
 */
public class AnswerGenerator {

    /**
     * Die Antwort und die Quellen (Quelle [1] = erstes Element).
     *
     * @param modelAsked {@code false}, wenn das Antwort-Modell nicht gefragt wurde (Frage passt
     *                   nicht zum Dokument oder bester Treffer unter der Schwelle)
     * @param searchText der Text, mit dem gesucht wurde (die übersetzte Frage); bei einer als
     *                   themenfremd erkannten Frage die Frage selbst
     * @param problems   was die Prüfung an der gelieferten Antwort noch findet – leer = in Ordnung
     * @param firstAttemptProblems was die Prüfung an der ersten Antwort fand (dann wurde sie neu
     *                   angefordert); leer, wenn die erste Antwort in Ordnung war
     */
    public record Answer(String text, List<SearchResult> sources, boolean modelAsked, String searchText,
            List<String> problems, List<String> firstAttemptProblems) {

        public Answer {
            problems = List.copyOf(problems);
            firstAttemptProblems = List.copyOf(firstAttemptProblems);
        }

        /** Antwort ohne Sprachmodell (themenfremd oder unter der Schwelle). */
        static Answer withoutModel(String text, List<SearchResult> sources, String searchText) {
            return new Answer(text, sources, false, searchText, List.of(), List.of());
        }

        /** Wurde die Antwort wegen gefundener Probleme neu angefordert? */
        public boolean corrected() {
            return !firstAttemptProblems.isEmpty();
        }
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
     * @param check        prüft die Antwort gegen die Quellen
     * @param correctionPrompt Nachricht an das Modell, wenn die Prüfung etwas findet; {@code %s}
     *                     wird durch die Liste der Probleme ersetzt
     */
    public record Settings(String systemPrompt, int topK, ContextExpander expander, double minScore,
            String noAnswerText, QueryRewriter rewriter, AnswerCheck check, String correctionPrompt) {

        /** Ohne Ergänzung der Treffer, ohne Schwelle, ohne Umformulierung und ohne Prüfung. */
        public static Settings of(String systemPrompt, int topK) {
            return new Settings(systemPrompt, topK, ContextExpander.NONE, Double.NEGATIVE_INFINITY,
                    "Keine passenden Quellen gefunden.", QueryRewriter.NONE, AnswerCheck.NONE, "%s");
        }

        public Settings withExpander(ContextExpander newExpander) {
            return new Settings(systemPrompt, topK, newExpander, minScore, noAnswerText, rewriter, check,
                    correctionPrompt);
        }

        public Settings withMinScore(double newMinScore, String newNoAnswerText) {
            return new Settings(systemPrompt, topK, expander, newMinScore, newNoAnswerText, rewriter, check,
                    correctionPrompt);
        }

        public Settings withRewriter(QueryRewriter newRewriter) {
            return new Settings(systemPrompt, topK, expander, minScore, noAnswerText, newRewriter, check,
                    correctionPrompt);
        }

        public Settings withCheck(AnswerCheck newCheck, String newCorrectionPrompt) {
            return new Settings(systemPrompt, topK, expander, minScore, noAnswerText, rewriter, newCheck,
                    newCorrectionPrompt);
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
        // 1. In die Sprache des Dokuments übersetzen – oder als themenfremd erkennen
        Optional<String> rewritten = settings.rewriter().rewrite(question);
        if (rewritten.isEmpty()) {
            return Answer.withoutModel(settings.noAnswerText(), List.of(), question);
        }
        String searchText = rewritten.get();

        // 2. Suchen und die Schwelle prüfen
        List<SearchResult> hits = retriever.search(searchText, settings.topK());
        if (hits.isEmpty() || hits.getFirst().score() < settings.minScore()) {
            return Answer.withoutModel(settings.noAnswerText(), hits, searchText);
        }

        // 3. Treffer ergänzen, 4. Originalfrage beantworten lassen
        List<SearchResult> sources = settings.expander().expand(hits);
        List<ChatMessage> conversation = new ArrayList<>(List.of(
                ChatMessage.system(settings.systemPrompt()),
                ChatMessage.user(buildUserMessage(question, sources))));
        String text = chatModel.chat(conversation);

        // 5. Prüfen – bei Problemen einmal nachbessern lassen (im selben Gespräch, damit das
        //    Modell seine erste Antwort und die Quellen sieht)
        List<String> problems = settings.check().problems(text, sources);
        if (problems.isEmpty()) {
            return new Answer(text, sources, true, searchText, List.of(), List.of());
        }
        conversation.add(ChatMessage.assistant(text));
        conversation.add(ChatMessage.user(settings.correctionPrompt()
                .formatted("- " + String.join("\n- ", problems))));
        String corrected = chatModel.chat(conversation);
        return new Answer(corrected, sources, true, searchText, settings.check().problems(corrected, sources),
                problems);
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
