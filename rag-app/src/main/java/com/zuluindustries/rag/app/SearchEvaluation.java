package com.zuluindustries.rag.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.zuluindustries.rag.core.ChatModel;
import com.zuluindustries.rag.core.ChatQueryRewriter;
import com.zuluindustries.rag.core.EmbeddingModel;
import com.zuluindustries.rag.core.QueryRewriter;
import com.zuluindustries.rag.core.Retriever;
import com.zuluindustries.rag.core.SearchResult;
import com.zuluindustries.rag.core.TermQueryRewriter;
import com.zuluindustries.rag.core.chunk.ChunkAssembler;
import com.zuluindustries.rag.store.memory.InMemoryVectorStore;

/**
 * Misst die Suchqualität mit Testfragen, deren richtige Antwort (Regelnummer
 * oder Definitionsbegriff) bekannt ist, und vergleicht, wie die Frage vor der
 * Suche in Regelsprache übersetzt wird:
 * <ul>
 * <li><b>Orig.</b> – die Frage unverändert (mit der Anweisung des Embedding-Modells)</li>
 * <li><b>A</b> – Frage + Fachbegriffe aus der Begriffsliste</li>
 * <li><b>B</b> – die Frage, vom Sprachmodell in Regelsprache umformuliert</li>
 * <li><b>C</b> – ein hypothetischer Absatz im Stil des Regelbuchs ("HyDE"); weil das
 * ein Text wie die gespeicherten Chunks ist und keine Frage, ohne Anweisung eingebettet</li>
 * </ul>
 *
 * <p>Kennzahlen: Treffer@1 / Treffer@5 (richtige Quelle auf Platz 1 / unter den
 * ersten fünf) und der beste Ähnlichkeitswert von Regelfragen und themenfremden
 * Fragen – als Grundlage für die Schwelle.
 */
public class SearchEvaluation {

    private static final int TOP_K = 10;

    /**
     * Eine Testfrage mit den Nummern, die als richtig gelten. "19" gilt auch für
     * "19.2a", "16.1" auch für "16.1a", aber nicht für "16.10".
     */
    private record TestQuestion(String question, List<String> expected) {
    }

    private static final List<TestQuestion> RULE_QUESTIONS = List.of(
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

    /** Umgangssprachlich – so, wie man auf dem Platz fragt (die ersten beiden aus echter Nutzung). */
    private static final List<TestQuestion> COLLOQUIAL_QUESTIONS = List.of(
            new TestQuestion("Ich habe ins wasser geschlagen, es ist gelb markiert. wie verhalte ich mich?",
                    List.of("17.1")),
            new TestQuestion("ich habe abgeschlagen, weiß aber nicht, ob ich meinen ball finden werde. was mache ich",
                    List.of("18.3", "Provisorischer Ball")),
            new TestQuestion("Mein Ball ist im Teich gelandet, da stehen rote Pfähle. Was jetzt?", List.of("17.1")),
            new TestQuestion("Ball ist weg, ich find ihn nicht mehr. Und nun?", List.of("18.2", "Verloren")));

    private static final List<TestQuestion> TEST_QUESTIONS =
            Stream.concat(RULE_QUESTIONS.stream(), COLLOQUIAL_QUESTIONS.stream()).toList();

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

    /**
     * Eine Art, zu einer Frage zu suchen. Suchtexte und deren Vektoren werden
     * vorab einmal berechnet – jede Übersetzung und jedes Einbetten kostet einen
     * Aufruf beim Anbieter, die Suche selbst danach nichts mehr.
     */
    private record Variant(String name, Retriever retriever, Map<String, String> searchTexts,
            Map<String, float[]> vectors, Set<String> rejected) {

        List<SearchResult> search(String question, int topK) {
            return retriever.search(vectors.get(question), topK, document -> true);
        }

        double bestScore(String question) {
            return search(question, 1).getFirst().score();
        }

        /** Hat der Übersetzer die Frage als themenfremd erkannt? (Gesucht wird dann mit der Frage selbst.) */
        boolean rejected(String question) {
            return rejected.contains(question);
        }
    }

    public static void main(String[] args) throws IOException {
        EmbeddingModel model = Scaleway.embeddingModel();
        InMemoryVectorStore store = SearchIndex.loadExisting();
        ChatModel chat = Scaleway.chatModel(0.0);

        Retriever instruct = new Retriever(model, store, Scaleway.QUERY_INSTRUCTION);
        Retriever plain = new Retriever(model, store, "");
        List<String> allQuestions = new ArrayList<>(TEST_QUESTIONS.stream().map(TestQuestion::question).toList());
        allQuestions.addAll(OFF_TOPIC_QUESTIONS);

        System.out.println("Übersetze " + allQuestions.size() + " Fragen mit drei Varianten (dauert etwa eine Minute) …");
        List<Variant> variants = List.of(
                variant("Orig.", QueryRewriter.NONE, instruct, allQuestions),
                variant("A", new TermQueryRewriter(chat, GolfRules.definitionTerms(store.documents()),
                        GolfRules.MAX_QUERY_TERMS), instruct, allQuestions),
                variant("B", new ChatQueryRewriter(chat, GolfRules.REPHRASE_PROMPT), instruct, allQuestions),
                variant("C", new ChatQueryRewriter(chat, GolfRules.HYPOTHETICAL_PASSAGE_PROMPT), plain, allQuestions));

        printRanks(variants);
        printSeparation(variants);
        printTranslations(variants);
    }

    private static Variant variant(String name, QueryRewriter rewriter, Retriever retriever, List<String> questions) {
        Map<String, String> searchTexts = new LinkedHashMap<>();
        Map<String, float[]> vectors = new LinkedHashMap<>();
        Set<String> rejected = new HashSet<>();
        for (String question : questions) {
            Optional<String> rewritten = rewriter.rewrite(question);
            if (rewritten.isEmpty()) {
                rejected.add(question);
            }
            String searchText = rewritten.orElse(question);
            searchTexts.put(question, searchText);
            vectors.put(question, retriever.embed(searchText));
        }
        return new Variant(name, retriever, searchTexts, vectors, rejected);
    }

    /** Platz der richtigen Quelle je Frage und Variante, dazu Treffer@1 / Treffer@5. */
    private static void printRanks(List<Variant> variants) {
        List<List<Integer>> ranks = variants.stream().map(SearchEvaluation::ranks).toList();

        System.out.println();
        System.out.println("Platz der ersten richtigen Antwort (– = nicht unter den ersten " + TOP_K + ")");
        System.out.println();
        StringBuilder header = new StringBuilder(" # ");
        variants.forEach(variant -> header.append(String.format(" %-5s", variant.name())));
        System.out.println(header.append("  Frage"));
        for (int q = 0; q < TEST_QUESTIONS.size(); q++) {
            if (q == RULE_QUESTIONS.size()) {
                System.out.println("    — umgangssprachlich —");
            }
            StringBuilder row = new StringBuilder(String.format("%2d ", q + 1));
            for (List<Integer> variantRanks : ranks) {
                row.append(String.format(" %-5s", format(variantRanks.get(q))));
            }
            System.out.println(row.append("  ").append(TEST_QUESTIONS.get(q).question()));
        }
        System.out.println();
        for (int v = 0; v < variants.size(); v++) {
            printSummary(String.format("%-6s", variants.get(v).name()), ranks.get(v));
        }
    }

    /**
     * Wie gut trennt der beste Ähnlichkeitswert Regelfragen von themenfremden
     * Fragen? Daraus ergibt sich die Schwelle: mit Sicherheitsabstand unter der
     * schwächsten Regelfrage.
     */
    private static void printSeparation(List<Variant> variants) {
        double safetyMargin = 0.05;
        System.out.println();
        System.out.println("Trennung Regelfragen / themenfremde Fragen (bester Ähnlichkeitswert)");
        for (Variant variant : variants) {
            String weakestRule = TEST_QUESTIONS.stream().map(TestQuestion::question)
                    .min((a, b) -> Double.compare(variant.bestScore(a), variant.bestScore(b))).orElseThrow();
            String strongestOffTopic = OFF_TOPIC_QUESTIONS.stream()
                    .max((a, b) -> Double.compare(variant.bestScore(a), variant.bestScore(b))).orElseThrow();
            double weakest = variant.bestScore(weakestRule);
            double strongest = variant.bestScore(strongestOffTopic);
            double suggestion = Math.floor((weakest - safetyMargin) * 100) / 100;
            long blocked = OFF_TOPIC_QUESTIONS.stream().filter(q -> variant.bestScore(q) < suggestion).count();
            List<String> offTopicPassed = OFF_TOPIC_QUESTIONS.stream().filter(q -> !variant.rejected(q)).toList();
            List<String> rulesRejected = TEST_QUESTIONS.stream().map(TestQuestion::question)
                    .filter(variant::rejected).toList();

            System.out.println();
            System.out.println("--- " + variant.name() + " ---");
            System.out.printf("  Schwächste Regelfrage:  %.3f  (%s)%n", weakest, weakestRule);
            System.out.printf("  Stärkste themenfremde:  %.3f  (%s)%n", strongest, strongestOffTopic);
            System.out.printf("  Abstand:                %.3f%n", weakest - strongest);
            System.out.printf("  Vorschlag Schwelle:     %.2f  → fängt %d von %d themenfremden Fragen ab%n",
                    suggestion, blocked, OFF_TOPIC_QUESTIONS.size());
            if (!variant.name().equals("Orig.")) {
                System.out.printf("  Als themenfremd erkannt (\"keine\"):  %d von %d themenfremden%n",
                        OFF_TOPIC_QUESTIONS.size() - offTopicPassed.size(), OFF_TOPIC_QUESTIONS.size());
                offTopicPassed.forEach(q -> System.out.println("      durchgelassen: " + q));
                System.out.printf("  Fälschlich abgewiesen:              %d von %d Regelfragen%n",
                        rulesRejected.size(), TEST_QUESTIONS.size());
                rulesRejected.forEach(q -> System.out.println("      abgewiesen: " + q));
            }
        }
    }

    /** Die Übersetzungen der umgangssprachlichen Fragen – zum Nachvollziehen. */
    private static void printTranslations(List<Variant> variants) {
        System.out.println();
        System.out.println("Übersetzungen der umgangssprachlichen Fragen");
        for (TestQuestion test : COLLOQUIAL_QUESTIONS) {
            System.out.println();
            System.out.println(test.question());
            for (Variant variant : variants.subList(1, variants.size())) {
                String text = variant.searchTexts().get(test.question()).replace('\n', ' ');
                System.out.printf("  %s: %s%n", variant.name(), variant.rejected(test.question()) ? "(als themenfremd abgewiesen)" : text);
            }
        }
    }

    /** Für jede Testfrage: Platz der ersten richtigen Antwort (1 = ganz oben), 0 = nicht gefunden. */
    private static List<Integer> ranks(Variant variant) {
        List<Integer> ranks = new ArrayList<>();
        for (TestQuestion test : TEST_QUESTIONS) {
            List<SearchResult> results = variant.search(test.question(), TOP_K);
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
