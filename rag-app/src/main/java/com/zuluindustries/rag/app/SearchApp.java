package com.zuluindustries.rag.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.Retriever;
import com.zuluindustries.rag.core.SearchResult;
import com.zuluindustries.rag.core.chunk.ChunkAssembler;

/**
 * Interaktive Suche in den Golfregeln: Frage eintippen, die passendsten Chunks
 * werden angezeigt. In Eclipse in der Console tippen; eine leere Zeile beendet
 * das Programm. Mit Programm-Argument wird nur diese eine Frage beantwortet.
 *
 * <p>Braucht den Suchindex (zuerst IndexApp ausführen) und die Umgebungsvariable
 * SCW_SECRET_KEY, weil auch die Frage eingebettet werden muss.
 */
public class SearchApp {

    private static final int TOP_K = 5;
    private static final int SNIPPET_LENGTH = 160;

    /** Ob Fragen mit der Anweisung des Modells beginnen – Entscheidung nach SearchEvaluation. */
    private static final String QUERY_PREFIX = Scaleway.QUERY_INSTRUCTION;

    public static void main(String[] args) throws IOException {
        Retriever retriever = new Retriever(Scaleway.embeddingModel(), SearchIndex.loadExisting(), QUERY_PREFIX);

        if (args.length > 0) {
            answer(retriever, String.join(" ", args));
            return;
        }

        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("\nFrage (leere Zeile beendet): ");
            String question = console.readLine();
            if (question == null || question.isBlank()) {
                return;
            }
            answer(retriever, question);
        }
    }

    private static void answer(Retriever retriever, String question) {
        List<SearchResult> results = retriever.search(question, TOP_K);
        System.out.println();
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            Document chunk = result.document();
            System.out.printf("%d. %.3f  %s%n", i + 1, result.score(),
                    chunk.metadata().get(ChunkAssembler.HEADING_KEY));
            System.out.printf("          Seite %s (gedruckt %s) · „%s“%n",
                    chunk.metadata().get(ChunkAssembler.PAGE_KEY),
                    chunk.metadata().getOrDefault(ChunkAssembler.PRINTED_PAGE_KEY, "–"),
                    snippet(chunk.text()));
        }
    }

    /** Der Anfang des Inhalts (ohne Überschriften-Kette), in einer Zeile. */
    private static String snippet(String chunkText) {
        int bodyStart = chunkText.indexOf("\n\n");
        String body = (bodyStart < 0 ? chunkText : chunkText.substring(bodyStart + 2)).replace('\n', ' ');
        return body.length() <= SNIPPET_LENGTH ? body : body.substring(0, SNIPPET_LENGTH) + " …";
    }
}
