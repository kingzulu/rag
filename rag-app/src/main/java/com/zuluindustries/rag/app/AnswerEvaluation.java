package com.zuluindustries.rag.app;

import java.io.IOException;
import java.util.List;

import com.zuluindustries.rag.core.AnswerGenerator;
import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.SearchResult;
import com.zuluindustries.rag.core.chunk.ChunkAssembler;
import com.zuluindustries.rag.store.memory.InMemoryVectorStore;

/**
 * Beantwortet eine feste Liste von Testfragen mit dem Assistenten aus AskApp
 * ({@link GolfAssistant}) und gibt Antworten und Quellen aus – zum Prüfen nach
 * jeder Änderung an Prompt oder Suche. Bewertet wird von Hand.
 *
 * <p>Antworten mit Temperatur 0, damit Unterschiede möglichst von der Änderung
 * kommen und nicht vom Zufall. Für einen direkten Vergleich zweier Varianten
 * eine zweite Variante ergänzen, die sich in genau einer Sache unterscheidet.
 */
public class AnswerEvaluation {

    private static final int MAX_SEARCH_TEXT_SHOWN = 300;

    private static final List<String> QUESTIONS = List.of(
            "Darf ich im Bunker vor dem Schlag den Sand berühren?",
            "Mein Ball liegt im tiefen Busch und ich kann ihn nicht spielen. Was kann ich tun?",
            "Wie viele Schläger darf ich höchstens in meiner Tasche haben?",
            "Was passiert, wenn mein Ball beim Putten den Flaggenstock trifft?",
            "Mein Ball liegt in einem Kaninchenloch. Bekomme ich Erleichterung?",
            "Wer hat die Open Championship 2024 gewonnen?",                         // Fangfrage
            "Wie hoch ist der Jahresbeitrag in meinem Golfclub?",                   // Fangfrage
            "Wie spielt man Stableford, und darf ich dabei einen Ball aufnehmen?",  // teils beantwortbar
            "Ich habe ins wasser geschlagen, es ist gelb markiert. wie verhalte ich mich?",      // umgangssprachlich
            "ich habe abgeschlagen, weiß aber nicht, ob ich meinen ball finden werde. was mache ich",
            "Wie verbessere ich meinen Abschlag?",                                  // Grenzfall (Technik)
            "Was kostet eine Runde Golf auf einem Platz in Bayern?");               // Grenzfall

    public static void main(String[] args) throws IOException {
        InMemoryVectorStore store = SearchIndex.loadExisting();
        AnswerGenerator assistant = GolfAssistant.create(store, Scaleway.chatModel(0.0));

        int failures = 0;
        for (int q = 0; q < QUESTIONS.size(); q++) {
            String question = QUESTIONS.get(q);
            System.out.println("=".repeat(100));
            System.out.println("Frage " + (q + 1) + ": " + question);
            System.out.println();
            // Ein Fehler (z. B. Zeitüberschreitung beim Anbieter) soll nicht die ganze Auswertung abbrechen.
            try {
                printAnswer(question, assistant.answer(question));
            } catch (RuntimeException e) {
                failures++;
                System.out.println("FEHLER: " + e.getMessage());
            }
        }
        if (failures > 0) {
            System.out.println();
            System.out.println(failures + " Antwort(en) fehlgeschlagen – siehe FEHLER oben.");
        }
    }

    private static void printAnswer(String question, AnswerGenerator.Answer answer) {
        if (!answer.searchText().equals(question)) {
            String shown = answer.searchText().replace('\n', ' ');
            if (shown.length() > MAX_SEARCH_TEXT_SHOWN) {
                shown = shown.substring(0, MAX_SEARCH_TEXT_SHOWN) + " …";
            }
            System.out.println("(gesucht mit: " + shown + ")");
            System.out.println();
        }
        System.out.println(answer.text());
        if (!answer.modelAsked()) {
            System.out.println(answer.sources().isEmpty()
                    ? "(Sprachmodell nicht gefragt – Frage passt nicht zu den Golfregeln)"
                    : "(Sprachmodell nicht gefragt – bester Treffer unter der Schwelle)");
        }
        printSources(answer.sources());
    }

    /** Quellen mit Ähnlichkeit; "erg." = Geschwister-Abschnitt. */
    private static void printSources(List<SearchResult> sources) {
        if (sources.isEmpty()) {
            return;
        }
        int chars = sources.stream().mapToInt(source -> source.document().text().length()).sum();
        System.out.println();
        System.out.println("Quellen (" + sources.size() + ", " + chars + " Zeichen):");
        for (int i = 0; i < sources.size(); i++) {
            Document chunk = sources.get(i).document();
            double score = sources.get(i).score();
            String scoreText = Double.isNaN(score) ? "  erg." : String.format("%.3f", score);
            System.out.printf("  [%d] %s  %s%n", i + 1, scoreText, chunk.metadata().get(ChunkAssembler.HEADING_KEY));
        }
    }
}
