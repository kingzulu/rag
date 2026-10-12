package com.zuluindustries.rag.core;

import java.util.List;

/**
 * Wie der {@link AnswerGenerator} zu einer Frage sucht. Er übergibt beides: die
 * Frage, wie sie gestellt wurde, und den Suchtext (die übersetzte Frage, siehe
 * {@link QueryRewriter}). Eine Suche kann einen davon nutzen oder beide.
 */
@FunctionalInterface
public interface Searcher {

    /** @return die {@code topK} passendsten Dokumente, mit ihrer Ähnlichkeit */
    List<SearchResult> search(String question, String searchText, int topK);

    /** Die einfache Suche: nur mit dem Suchtext. */
    static Searcher of(Retriever retriever) {
        return (question, searchText, topK) -> retriever.search(searchText, topK);
    }
}
