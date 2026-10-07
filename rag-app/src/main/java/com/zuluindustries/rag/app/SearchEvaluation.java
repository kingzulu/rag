package com.zuluindustries.rag.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.zuluindustries.rag.core.EmbeddingModel;
import com.zuluindustries.rag.core.Retriever;
import com.zuluindustries.rag.core.SearchResult;
import com.zuluindustries.rag.core.VectorStore;
import com.zuluindustries.rag.core.chunk.ChunkAssembler;

/**
 * Misst die Suchqualität mit Testfragen, deren richtige Antwort (Regelnummer
 * oder Definitionsbegriff) bekannt ist – und vergleicht dabei zwei Varianten:
 * Fragen ohne Präfix und Fragen mit der Anweisung, die das Modell erwartet.
 *
 * <p>Kennzahlen:
 * <ul>
 * <li>Treffer@1: Anteil der Fragen, bei denen der erste Treffer richtig ist</li>
 * <li>Treffer@5: Anteil der Fragen, bei denen ein richtiger Treffer unter den ersten fünf ist</li>
 * </ul>
 *
 * <p>Zusätzlich: der beste Ähnlichkeitswert je Frage für Regelfragen und
 * themenfremde Fragen, als Grundlage für die Schwelle in AskApp.
 */
public class SearchEvaluation {

    private static final int TOP_K = 10;

    /**
     * Eine Testfrage mit den Nummern, die als richtig gelten. "19" gilt auch für
     * "19.2a", "16.1" auch für "16.1a", aber nicht für "16.10".
     */
    private record TestQuestion(String question, List<String> expected) {
    }

    private static final List<TestQuestion> QUESTIONS = List.of(
            new TestQuestion("Darf ich im Bunker vor dem Schlag den Sand berühren?", List.of("12.2b")),
            new TestQuestion("Mein Ball liegt im tiefen Busch und ich kann ihn nicht spielen. Was kann ich tun?",
                    List.of("19")),
            new TestQuestion("Wie lange darf ich nach meinem Ball suchen?", List.of("18.2a", "Verloren")),
            new TestQuestion("Wann darf ich einen provisorischen Ball spielen?",
                    List.of("18.3", "Provisorischer Ball")),
            new TestQuestion("Mein Ball ist in einer roten Penalty Area gelandet. Welche Möglichkeiten habe ich?",
                    List.of("17.1d")),
            new TestQuestion("Wie viele Schläger darf ich höchstens in meiner Tasche haben?", List.of("4.1b")),
            new TestQuestion("Darf ich auf dem Grün Pitchmarken ausbessern?", List.of("13.1c")),
            new TestQuestion("Was passiert, wenn mein Ball beim Putten den Flaggenstock trifft?", List.of("13.2")),
            new TestQuestion("Darf mein Caddie hinter mir stehen, während ich mich zum Schlag aufstelle?",
                    List.of("10.2b")),
            new TestQuestion("Mein Ball hat sich bewegt, als ich den Schläger hinter ihm aufgesetzt habe.",
                    List.of("9.4")),
            new TestQuestion("Was ist eine Grundstrafe?", List.of("Grundstrafe")),
            new TestQuestion("Wie droppe ich einen Ball richtig?", List.of("14.3b")),
            new TestQuestion("Mein Ball liegt in einem Kaninchenloch. Bekomme ich Erleichterung?",
                    List.of("16.1a", "16.1b", "Tierloch")),   // nicht "16.1": 16.1c handelt vom Bunker
            new TestQuestion("Mein Ball steckt in seinem eigenen Einschlagloch. Bekomme ich Erleichterung?",
                    List.of("16.3", "Eingebettet")),
            new TestQuestion("Wer gewinnt im Zählspiel?", List.of("3.3a")),
            new TestQuestion("Wie schnell muss ich spielen?", List.of("5.6b")));

    /** Fragen, auf die die Golfregeln keine Antwort haben – zum Bestimmen der Schwelle. */
    private static final List<String> OFF_TOPIC_QUESTIONS = List.of(
            "Wer hat die Open Championship 2024 gewonnen?",
            "Wie hoch ist der Jahresbeitrag in meinem Golfclub?",
            "Wie wird das Wetter morgen in München?",
            "Wie koche ich Spaghetti Carbonara?",
            "Wie mache ich meine Steuererklärung?",
            "Erkläre mir die Relativitätstheorie.",
            // Grenzfälle: Golf, aber keine Regelfrage
            "Welchen Golfschläger soll ich als Anfänger kaufen?",
            "Wie verbessere ich meinen Abschlag?",
            "Was kostet eine Runde Golf auf einem Platz in Bayern?");

    public static void main(String[] args) throws IOException {
        EmbeddingModel model = Scaleway.embeddingModel();
        VectorStore store = SearchIndex.loadExisting();

        Retriever plain = new Retriever(model, store, "");
        Retriever instruct = new Retriever(model, store, Scaleway.QUERY_INSTRUCTION);
        List<Integer> ranksPlain = ranks(plain);
        List<Integer> ranksInstruct = ranks(instruct);

        System.out.println("Platz der ersten richtigen Antwort (– = nicht unter den ersten " + TOP_K + ")");
        System.out.println();
        System.out.println(" #  ohne  mit   Frage");
        System.out.println("    Präfix Anw.");
        for (int i = 0; i < QUESTIONS.size(); i++) {
            System.out.printf("%2d   %-4s %-4s  %s%n", i + 1, format(ranksPlain.get(i)), format(ranksInstruct.get(i)),
                    QUESTIONS.get(i).question());
        }
        System.out.println();
        printSummary("Ohne Präfix     ", ranksPlain);
        printSummary("Mit Anweisung   ", ranksInstruct);

        printBestScores(instruct);
    }

    /** Ein Ähnlichkeitswert mit der zugehörigen Frage. */
    private record ScoredQuestion(double score, String question) {
    }

    /**
     * Gibt für jede Frage den besten Ähnlichkeitswert aus (mit Anweisung, wie in
     * AskApp) – getrennt nach Regelfragen und themenfremden Fragen – und schlägt
     * eine Schwelle vor: mit Sicherheitsabstand unter der schwächsten Regelfrage.
     */
    private static void printBestScores(Retriever retriever) {
        List<ScoredQuestion> rules = bestScores(retriever, QUESTIONS.stream().map(TestQuestion::question).toList());
        List<ScoredQuestion> offTopic = bestScores(retriever, OFF_TOPIC_QUESTIONS);

        System.out.println();
        System.out.println("Bester Ähnlichkeitswert je Frage (mit Anweisung)");
        System.out.println();
        System.out.println("Regelfragen:");
        rules.forEach(scored -> System.out.printf("  %.3f  %s%n", scored.score(), scored.question()));
        System.out.println();
        System.out.println("Themenfremde Fragen und Grenzfälle:");
        offTopic.forEach(scored -> System.out.printf("  %.3f  %s%n", scored.score(), scored.question()));

        ScoredQuestion weakestRule = rules.getLast();
        ScoredQuestion strongestOffTopic = offTopic.getFirst();
        double safetyMargin = 0.05;
        double suggestion = Math.floor((weakestRule.score() - safetyMargin) * 100) / 100;
        long blocked = offTopic.stream().filter(scored -> scored.score() < suggestion).count();

        System.out.println();
        System.out.printf("Schwächste Regelfrage:    %.3f  (%s)%n", weakestRule.score(), weakestRule.question());
        System.out.printf("Stärkste themenfremde:    %.3f  (%s)%n", strongestOffTopic.score(),
                strongestOffTopic.question());
        System.out.printf("Abstand:                  %.3f%n", weakestRule.score() - strongestOffTopic.score());
        System.out.printf("Vorschlag Schwelle:       %.2f  (%.2f unter der schwächsten Regelfrage)%n",
                suggestion, safetyMargin);
        System.out.printf("Damit abgefangen:         %d von %d themenfremden Fragen%n", blocked, offTopic.size());
    }

    /** Bester Ähnlichkeitswert je Frage, absteigend sortiert. */
    private static List<ScoredQuestion> bestScores(Retriever retriever, List<String> questions) {
        return questions.stream()
                .map(question -> new ScoredQuestion(retriever.search(question, 1).getFirst().score(), question))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();
    }

    /** Für jede Testfrage: Platz der ersten richtigen Antwort (1 = ganz oben), 0 = nicht gefunden. */
    private static List<Integer> ranks(Retriever retriever) {
        List<Integer> ranks = new ArrayList<>();
        for (TestQuestion test : QUESTIONS) {
            List<SearchResult> results = retriever.search(test.question(), TOP_K);
            int rank = 0;
            for (int i = 0; i < results.size() && rank == 0; i++) {
                String number = results.get(i).document().metadata().get(ChunkAssembler.NUMBER_KEY);
                if (matchesAny(number, test.expected())) {
                    rank = i + 1;
                }
            }
            ranks.add(rank);
        }
        return ranks;
    }

    private static boolean matchesAny(String number, List<String> expected) {
        return number != null && expected.stream().anyMatch(e -> matches(number, e));
    }

    /** "19.2a" passt zu "19" und "19.2"; "16.10" passt nicht zu "16.1". */
    private static boolean matches(String number, String expected) {
        if (number.equals(expected)) {
            return true;
        }
        if (!number.startsWith(expected) || number.length() == expected.length()) {
            return false;
        }
        char next = number.charAt(expected.length());
        return next == '.' || Character.isLetter(next);
    }

    private static void printSummary(String label, List<Integer> ranks) {
        long top1 = ranks.stream().filter(rank -> rank == 1).count();
        long top5 = ranks.stream().filter(rank -> rank >= 1 && rank <= 5).count();
        System.out.printf("%s Treffer@1: %2d/%d   Treffer@5: %2d/%d%n", label, top1, ranks.size(), top5, ranks.size());
    }

    private static String format(int rank) {
        return rank == 0 ? "–" : String.valueOf(rank);
    }
}
