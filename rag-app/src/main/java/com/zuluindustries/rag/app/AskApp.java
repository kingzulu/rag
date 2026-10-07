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
import com.zuluindustries.rag.core.chunk.SiblingExpander;
import com.zuluindustries.rag.store.memory.InMemoryVectorStore;

/**
 * Fragen zu den Golfregeln stellen und eine formulierte Antwort mit Quellen
 * bekommen. In Eclipse in der Console tippen; eine leere Zeile beendet das
 * Programm. Mit Programm-Argument wird nur diese eine Frage beantwortet.
 *
 * <p>Die Suchtreffer werden um ihre Geschwister-Abschnitte ergänzt (z. B. 19.2a
 * und 19.2b zu einem Treffer in 19.2c), damit das Modell vollständige Quellen hat.
 *
 * <p>Braucht den Suchindex (zuerst IndexApp ausführen) und die Umgebungsvariable
 * SCW_SECRET_KEY.
 */
public class AskApp {

    public static void main(String[] args) throws IOException {
        InMemoryVectorStore store = SearchIndex.loadExisting();
        Retriever retriever = new Retriever(Scaleway.embeddingModel(), store, Scaleway.QUERY_INSTRUCTION);
        SiblingExpander siblings = new SiblingExpander(store.documents(), GolfRules.MAX_CONTEXT_CHUNKS,
                GolfRules.MAX_CONTEXT_CHARS);
        AnswerGenerator generator = new AnswerGenerator(retriever, Scaleway.chatModel(), GolfRules.SYSTEM_PROMPT,
                GolfRules.ANSWER_TOP_K, siblings);
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
        System.out.println("Quellen (" + millis + " ms; \"erg.\" = als Geschwister-Abschnitt ergänzt):");
        List<SearchResult> sources = answer.sources();
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
