package com.zuluindustries.rag.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import com.zuluindustries.rag.core.AnswerGenerator;
import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.SearchResult;
import com.zuluindustries.rag.core.chunk.ChunkAssembler;

/**
 * Fragen zu den Golfregeln stellen und eine formulierte Antwort mit Quellen
 * bekommen. In Eclipse in der Console tippen; eine leere Zeile beendet das
 * Programm. Mit Programm-Argument wird nur diese eine Frage beantwortet.
 *
 * <p>Wie der Assistent arbeitet, steht in {@link GolfAssistant}: Die Frage wird
 * zuerst in eine hypothetische Regelstelle übersetzt, damit gesucht und die
 * Antwort aus den gefundenen Quellen formuliert.
 *
 * <p>Braucht den Suchindex (zuerst IndexApp ausführen) und die Umgebungsvariable
 * SCW_SECRET_KEY.
 */
public class AskApp {

    private static final int MAX_SEARCH_TEXT_SHOWN = 300;

    public static void main(String[] args) throws IOException {
        AnswerGenerator generator = GolfAssistant.create(SearchIndex.loadExisting(), Scaleway.chatModel());
        System.out.println("Golfregel-Assistent (" + Scaleway.CHAT_MODEL + ")");

        if (args.length > 0) {
            ask(generator, String.join(" ", args));
            return;
        }

        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("\nFrage (leere Zeile beendet): ");
            String question = console.readLine();
            if (question == null || question.isBlank()) {
                return;
            }
            ask(generator, question);
        }
    }

    private static void ask(AnswerGenerator generator, String question) {
        long start = System.nanoTime();
        AnswerGenerator.Answer answer = generator.answer(question);
        long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.println();
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
            if (answer.sources().isEmpty()) {
                System.out.println("(Sprachmodell nicht gefragt: Frage passt nicht zu den Golfregeln)");
            } else {
                System.out.printf("(Sprachmodell nicht gefragt: bester Treffer %.3f liegt unter der Schwelle %.2f)%n",
                        answer.sources().getFirst().score(), Scaleway.MIN_ANSWER_SCORE);
            }
        }

        List<SearchResult> sources = answer.sources();
        if (sources.isEmpty()) {
            System.out.println("(" + millis + " ms)");
            return;
        }
        System.out.println();
        System.out.println("Quellen (" + millis + " ms; \"erg.\" = als Geschwister-Abschnitt ergänzt):");
        for (int i = 0; i < sources.size(); i++) {
            Document chunk = sources.get(i).document();
            double score = sources.get(i).score();
            System.out.printf("  [%d] %s  %s  (Seite %s, gedruckt %s)%n", i + 1,
                    Double.isNaN(score) ? " erg." : String.format("%.3f", score),
                    chunk.metadata().get(ChunkAssembler.HEADING_KEY),
                    chunk.metadata().get(ChunkAssembler.PAGE_KEY),
                    chunk.metadata().getOrDefault(ChunkAssembler.PRINTED_PAGE_KEY, "–"));
        }
    }
}
