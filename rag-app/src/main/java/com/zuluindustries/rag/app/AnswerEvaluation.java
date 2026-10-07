package com.zuluindustries.rag.app;

import java.io.IOException;
import java.util.List;

import com.zuluindustries.rag.core.AnswerGenerator;
import com.zuluindustries.rag.core.ChatModel;
import com.zuluindustries.rag.core.ContextExpander;
import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.Retriever;
import com.zuluindustries.rag.core.SearchResult;
import com.zuluindustries.rag.core.chunk.ChunkAssembler;
import com.zuluindustries.rag.core.chunk.SiblingExpander;
import com.zuluindustries.rag.store.memory.InMemoryVectorStore;

/**
 * Beantwortet eine feste Liste von Testfragen – mit einer oder mehreren Varianten
 * – und gibt die Antworten untereinander aus, zum Vergleichen nach jeder
 * Änderung an Prompt oder Suche. Bewertet wird von Hand.
 *
 * <p>Temperatur 0, damit Unterschiede von der Variante kommen und nicht vom Zufall.
 * Kosten: etwa 20 Antworten zu je rund 3.000–5.000 Tokens (Fragen unter der
 * Schwelle kosten nur das Einbetten der Frage).
 */
public class AnswerEvaluation {

    private static final int TOP_K = GolfRules.ANSWER_TOP_K;

    private static final List<String> QUESTIONS = List.of(
            "Darf ich im Bunker vor dem Schlag den Sand berühren?",
            "Mein Ball liegt im tiefen Busch und ich kann ihn nicht spielen. Was kann ich tun?",
            "Wie viele Schläger darf ich höchstens in meiner Tasche haben?",
            "Was passiert, wenn mein Ball beim Putten den Flaggenstock trifft?",
            "Mein Ball liegt in einem Kaninchenloch. Bekomme ich Erleichterung?",
            "Wer hat die Open Championship 2024 gewonnen?",                         // Fangfrage
            "Wie hoch ist der Jahresbeitrag in meinem Golfclub?",                   // Fangfrage
            "Wie spielt man Stableford, und darf ich dabei einen Ball aufnehmen?",  // teils beantwortbar
            "Wie verbessere ich meinen Abschlag?",                                  // Grenzfall (Technik)
            "Was kostet eine Runde Golf auf einem Platz in Bayern?");               // Grenzfall

    /** Eine Variante, die verglichen wird. */
    private record Variant(String name, AnswerGenerator generator) {
    }

    public static void main(String[] args) throws IOException {
        InMemoryVectorStore store = SearchIndex.loadExisting();
        Retriever retriever = new Retriever(Scaleway.embeddingModel(), store, Scaleway.QUERY_INSTRUCTION);
        ChatModel chatModel = Scaleway.chatModel(0.0);
        SiblingExpander siblings = new SiblingExpander(store.documents(), GolfRules.MAX_CONTEXT_CHUNKS,
                GolfRules.MAX_CONTEXT_CHARS);

        // Für einen Vergleich eine zweite Variante ergänzen, die sich in genau einer Sache unterscheidet.
        List<Variant> variants = List.of(
                new Variant("aktuell (wie AskApp)", new AnswerGenerator(retriever, chatModel,
                        askAppSettings(GolfRules.SYSTEM_PROMPT, siblings))));

        int failures = 0;
        for (int q = 0; q < QUESTIONS.size(); q++) {
            String question = QUESTIONS.get(q);
            System.out.println("=".repeat(100));
            System.out.println("Frage " + (q + 1) + ": " + question);

            for (Variant variant : variants) {
                System.out.println();
                System.out.println("--- " + variant.name() + " ---");
                // Ein Fehler (z. B. Zeitüberschreitung beim Anbieter) soll nicht die ganze Auswertung abbrechen.
                try {
                    AnswerGenerator.Answer answer = variant.generator().answer(question);
                    System.out.println(answer.text());
                    if (!answer.modelAsked()) {
                        System.out.println("(Sprachmodell nicht gefragt – bester Treffer unter der Schwelle)");
                    }
                    printSources(answer.sources());
                } catch (RuntimeException e) {
                    failures++;
                    System.out.println("FEHLER: " + e.getMessage());
                }
            }
        }
        if (failures > 0) {
            System.out.println();
            System.out.println(failures + " Antwort(en) fehlgeschlagen – siehe FEHLER oben.");
        }
    }

    /** Dieselben Einstellungen wie in AskApp (Schwelle), mit wählbarer Anweisung und Ergänzung. */
    private static AnswerGenerator.Settings askAppSettings(String systemPrompt, ContextExpander expander) {
        return AnswerGenerator.Settings.of(systemPrompt, TOP_K)
                .withExpander(expander)
                .withMinScore(Scaleway.MIN_ANSWER_SCORE, GolfRules.NO_ANSWER_TEXT);
    }

    /** Quellen mit Ähnlichkeit; "erg." = Geschwister-Abschnitt, "def." = ergänzte Definition. */
    private static void printSources(List<SearchResult> sources) {
        int chars = sources.stream().mapToInt(source -> source.document().text().length()).sum();
        System.out.println();
        System.out.println("Quellen (" + sources.size() + ", " + chars + " Zeichen):");
        for (int i = 0; i < sources.size(); i++) {
            Document chunk = sources.get(i).document();
            double score = sources.get(i).score();
            String scoreText = !Double.isNaN(score) ? String.format("%.3f", score)
                    : GolfRules.isDefinition(chunk) ? "  def." : "  erg.";
            System.out.printf("  [%d] %s  %s%n", i + 1, scoreText, chunk.metadata().get(ChunkAssembler.HEADING_KEY));
        }
    }
}
