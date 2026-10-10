package com.zuluindustries.rag.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Meldet Begriffe, die in einer Antwort nicht vorkommen dürfen – z. B. Begriffe
 * aus einer veralteten Fassung des Dokuments, die das Sprachmodell aus dem
 * Training kennt.
 */
public class ForbiddenTermCheck implements AnswerCheck {

    private final Pattern terms;
    private final String label;

    /**
     * @param terms die verbotenen Begriffe als Muster
     * @param label wie ein Fund gemeldet wird, z. B. "Begriff aus den Regeln vor 2019"
     */
    public ForbiddenTermCheck(Pattern terms, String label) {
        this.terms = terms;
        this.label = label;
    }

    @Override
    public List<String> problems(String answer, List<SearchResult> sources) {
        List<String> problems = new ArrayList<>();
        Matcher matcher = terms.matcher(answer);
        while (matcher.find()) {
            problems.add(label + ": „" + matcher.group() + "“");
        }
        return List.copyOf(problems);
    }
}
