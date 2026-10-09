package com.zuluindustries.rag.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Ergänzt eine Frage um Fachbegriffe des Dokuments ("Query Expansion"): Ein
 * Sprachmodell wählt aus einer vorgegebenen Begriffsliste die Begriffe, unter
 * denen die Antwort zu finden ist. Gesucht wird dann mit Frage und Begriffen:
 *
 * <pre>
 * "Mein Ball liegt in einem Kaninchenloch …"
 *   → "Mein Ball liegt in einem Kaninchenloch … (Tierloch, Ungewöhnliche Platzverhältnisse)"
 * </pre>
 *
 * <ul>
 * <li>Nur Begriffe aus der Liste werden übernommen – erfundene Begriffe des
 * Modells werden verworfen.</li>
 * <li>Ergänzt statt ersetzt: Der Sinn der Originalfrage bleibt für die Suche erhalten.</li>
 * <li>Die Anweisung ist bewusst allgemein gehalten und enthält keine Beispiele
 * aus den Testfragen ("Data Leakage").</li>
 * </ul>
 */
public class TermQueryRewriter implements QueryRewriter {

    static final String SYSTEM_PROMPT = """
            Du hilfst bei der Suche in einem Regelwerk. Wähle aus der Liste der Fachbegriffe die Begriffe, \
            unter denen die Antwort auf die Frage im Regelwerk zu finden ist. Alltagswörter in der Frage \
            können einem Fachbegriff entsprechen, der anders lautet.
            Antworte nur mit den Begriffen, durch Komma getrennt und genau so geschrieben wie in der Liste – \
            höchstens %d. Passt kein Begriff, antworte nur mit: keine
            """;

    private final ChatModel chatModel;
    private final Map<String, String> vocabulary = new LinkedHashMap<>();   // kleingeschrieben → Original
    private final int maxTerms;

    /**
     * @param chatModel  das Sprachmodell, das die Begriffe auswählt
     * @param vocabulary die erlaubten Fachbegriffe (z. B. alle definierten Begriffe)
     * @param maxTerms   höchstens so viele Begriffe werden ergänzt
     */
    public TermQueryRewriter(ChatModel chatModel, List<String> vocabulary, int maxTerms) {
        this.chatModel = chatModel;
        vocabulary.forEach(term -> this.vocabulary.put(term.toLowerCase(Locale.ROOT), term));
        this.maxTerms = maxTerms;
    }

    /** Findet das Modell keine passenden Begriffe, wird mit der unveränderten Frage gesucht. */
    @Override
    public Optional<String> rewrite(String question) {
        List<String> terms = terms(question);
        return Optional.of(terms.isEmpty() ? question : question + " (" + String.join(", ", terms) + ")");
    }

    /** Die Begriffe, die das Modell für die Frage auswählt – nur solche aus der Liste. */
    public List<String> terms(String question) {
        String answer = chatModel.chat(List.of(
                ChatMessage.system(SYSTEM_PROMPT.formatted(maxTerms)),
                ChatMessage.user("Fachbegriffe: " + String.join(", ", vocabulary.values())
                        + "\n\nFrage: " + question)));

        List<String> terms = new ArrayList<>();
        Arrays.stream(answer.split("[,;\\n]"))
                .map(term -> term.strip().replaceAll("^[\"„“'*-]+|[\"„“'*.]+$", "").strip())
                .map(term -> vocabulary.get(term.toLowerCase(Locale.ROOT)))   // null = nicht in der Liste
                .filter(term -> term != null && !terms.contains(term))
                .limit(maxTerms)
                .forEach(terms::add);
        return terms;
    }
}
