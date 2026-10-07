package com.zuluindustries.rag.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import com.zuluindustries.rag.core.AnswerGenerator;
import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.Retriever;
import com.zuluindustries.rag.core.SearchResult;
import com.zuluindustries.rag.core.chunk.ChunkAssembler;

/**
 * Fragen zu den Golfregeln stellen und eine formulierte Antwort mit Quellen
 * bekommen. In Eclipse in der Console tippen; eine leere Zeile beendet das
 * Programm. Mit Programm-Argument wird nur diese eine Frage beantwortet.
 *
 * <p>Braucht den Suchindex (zuerst IndexApp ausführen) und die Umgebungsvariable
 * SCW_SECRET_KEY.
 */
public class AskApp {

    /** So viele Chunks bekommt das Sprachmodell als Quellen. */
    private static final int TOP_K = 5;

    public static void main(String[] args) throws IOException {
        Retriever retriever = new Retriever(Scaleway.embeddingModel(), SearchIndex.loadExisting(),
                Scaleway.QUERY_INSTRUCTION);
        AnswerGenerator generator = new AnswerGenerator(retriever, Scaleway.chatModel(), GolfRules.SYSTEM_PROMPT,
                TOP_K);
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
        System.out.println(answer.text());
        System.out.println();
        System.out.println("Quellen (" + millis + " ms):");
        List<SearchResult> sources = answer.sources();
        for (int i = 0; i < sources.size(); i++) {
            Document chunk = sources.get(i).document();
            System.out.printf("  [%d] %.3f  %s  (Seite %s, gedruckt %s)%n", i + 1, sources.get(i).score(),
                    chunk.metadata().get(ChunkAssembler.HEADING_KEY),
                    chunk.metadata().get(ChunkAssembler.PAGE_KEY),
                    chunk.metadata().getOrDefault(ChunkAssembler.PRINTED_PAGE_KEY, "–"));
        }
    }
}
