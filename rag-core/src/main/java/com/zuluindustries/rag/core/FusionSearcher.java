package com.zuluindustries.rag.core;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Sucht zweimal – mit der Frage und mit dem Suchtext – und führt beide
 * Trefferlisten mit {@link RankFusion} zusammen. Hängt die Suche nur an einem
 * Suchtext (z. B. einem erfundenen HyDE-Absatz), schlagen dessen Ausreißer voll
 * durch; die Frage selbst gleicht das aus.
 *
 * <p>Jeder Retriever bettet auf seine Art ein: die Frage z. B. mit der Anweisung
 * für Suchanfragen, den Suchtext (ein Absatz wie die Dokumente) ohne.
 *
 * <p>Die Reihenfolge der Treffer kommt aus der Zusammenführung, ihr Wert ist die
 * Ähnlichkeit zum Suchtext: RRF-Punkte sagen nichts über Ähnlichkeit, und so bleibt
 * eine Schwelle, die für die Suche mit dem Suchtext gemessen wurde, gültig. Darum
 * ist der beste Wert nicht unbedingt der des ersten Treffers.
 */
public class FusionSearcher implements Searcher {

    private final Retriever questionRetriever;
    private final Retriever textRetriever;
    private final int candidates;
    private final int k;

    /**
     * @param questionRetriever bettet die Frage ein
     * @param textRetriever     bettet den Suchtext ein; seine Ähnlichkeit wird zum Wert der Treffer
     * @param candidates        so viele Treffer jeder Einzelsuche gehen in die Zusammenführung
     * @param k                 Dämpfung der vorderen Plätze, üblich {@value RankFusion#DEFAULT_K}
     */
    public FusionSearcher(Retriever questionRetriever, Retriever textRetriever, int candidates, int k) {
        this.questionRetriever = questionRetriever;
        this.textRetriever = textRetriever;
        this.candidates = candidates;
        this.k = k;
    }

    @Override
    public List<SearchResult> search(String question, String searchText, int topK) {
        return search(questionRetriever.embed(question), textRetriever.embed(searchText), topK, document -> true);
    }

    /** Mit schon berechneten Vektoren – z. B. in einer Auswertung, die jede Frage nur einmal einbettet. */
    public List<SearchResult> search(float[] questionVector, float[] textVector, int topK, Predicate<Document> filter) {
        List<Document> best = RankFusion.fuse(List.of(
                questionRetriever.search(questionVector, candidates, filter),
                textRetriever.search(textVector, candidates, filter)), k)
                .stream().limit(topK).map(SearchResult::document).toList();
        if (best.isEmpty()) {
            return List.of();
        }
        Set<String> ids = best.stream().map(Document::id).collect(Collectors.toSet());
        Map<String, Double> similarity = textRetriever.search(textVector, ids.size(), document -> ids.contains(document.id()))
                .stream().collect(Collectors.toMap(result -> result.document().id(), SearchResult::score));
        return best.stream().map(document -> new SearchResult(document, similarity.get(document.id()))).toList();
    }
}
