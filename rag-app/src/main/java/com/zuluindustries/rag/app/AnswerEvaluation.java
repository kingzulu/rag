package com.zuluindustries.rag.app;

import java.io.IOException;
import java.util.List;

import com.zuluindustries.rag.core.AnswerGenerator;
import com.zuluindustries.rag.core.ChatModel;
import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.Retriever;
import com.zuluindustries.rag.core.SearchResult;
import com.zuluindustries.rag.core.chunk.ChunkAssembler;

/**
 * Beantwortet eine feste Liste von Testfragen mit zwei Varianten (z. B. zwei
 * Fassungen der Systemanweisung) und gibt die Antworten untereinander aus – zum Vergleichen nach jeder
 * Änderung an Prompt oder Suche. Bewertet wird von Hand.
 *
 * <p>Kosten: etwa 16 Antworten zu je rund 3.000 Tokens.
 */
public class AnswerEvaluation {

    private static final int TOP_K = 5;

    private static final List<String> QUESTIONS = List.of(
            "Darf ich im Bunker vor dem Schlag den Sand berühren?",
            "Mein Ball liegt im tiefen Busch und ich kann ihn nicht spielen. Was kann ich tun?",
            "Wie viele Schläger darf ich höchstens in meiner Tasche haben?",
            "Was passiert, wenn mein Ball beim Putten den Flaggenstock trifft?",
            "Mein Ball liegt in einem Kaninchenloch. Bekomme ich Erleichterung?",
            "Wer hat die Open Championship 2024 gewonnen?",                         // Fangfrage
            "Wie hoch ist der Jahresbeitrag in meinem Golfclub?",                   // Fangfrage
            "Wie spielt man Stableford, und darf ich dabei einen Ball aufnehmen?"); // teils beantwortbar

    /** Eine Variante, die verglichen wird. */
    private record Variant(String name, AnswerGenerator generator) {
    }

    public static void main(String[] args) throws IOException {
        Retriever retriever = new Retriever(Scaleway.embeddingModel(), SearchIndex.loadExisting(),
                Scaleway.QUERY_INSTRUCTION);
        ChatModel chatModel = Scaleway.chatModel();
        List<Variant> variants = List.of(
                new Variant("Anweisung V2",
                        new AnswerGenerator(retriever, chatModel, GolfRules.SYSTEM_PROMPT_V2, TOP_K)),
                new Variant("Anweisung V3 (aktuell)",
                        new AnswerGenerator(retriever, chatModel, GolfRules.SYSTEM_PROMPT, TOP_K)));

        for (int q = 0; q < QUESTIONS.size(); q++) {
            String question = QUESTIONS.get(q);
            System.out.println("=".repeat(100));
            System.out.println("Frage " + (q + 1) + ": " + question);

            List<SearchResult> sources = null;
            for (Variant variant : variants) {
                AnswerGenerator.Answer answer = variant.generator().answer(question);
                sources = answer.sources();
                System.out.println();
                System.out.println("--- " + variant.name() + " ---");
                System.out.println(answer.text());
            }
            printSources(sources);
        }
    }

    private static void printSources(List<SearchResult> sources) {
        System.out.println();
        System.out.printf("Quellen (bester Wert: %.3f):%n", sources.getFirst().score());
        for (int i = 0; i < sources.size(); i++) {
            Document chunk = sources.get(i).document();
            System.out.printf("  [%d] %.3f  %s%n", i + 1, sources.get(i).score(),
                    chunk.metadata().get(ChunkAssembler.HEADING_KEY));
        }
    }
}
