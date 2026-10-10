package com.zuluindustries.rag.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Prüft eine fertige Antwort gegen die Quellen, aus denen sie entstanden ist –
 * z. B. ob jede Regelnummer in einer Quelle steht. Findet die Prüfung etwas,
 * fordert der {@link AnswerGenerator} die Antwort einmal neu an.
 *
 * <p>Die Probleme werden dem Sprachmodell bei der Nachbesserung gezeigt; sie
 * sollten darum für das Modell verständlich formuliert sein.
 */
@FunctionalInterface
public interface AnswerCheck {

    /** Prüft nichts. */
    AnswerCheck NONE = (answer, sources) -> List.of();

    /** @return die gefundenen Probleme; leer, wenn die Antwort in Ordnung ist */
    List<String> problems(String answer, List<SearchResult> sources);

    /** Alle Prüfungen nacheinander; die Probleme werden gesammelt. */
    static AnswerCheck all(AnswerCheck... checks) {
        return (answer, sources) -> {
            List<String> problems = new ArrayList<>();
            for (AnswerCheck check : checks) {
                problems.addAll(check.problems(answer, sources));
            }
            return List.copyOf(problems);
        };
    }
}
