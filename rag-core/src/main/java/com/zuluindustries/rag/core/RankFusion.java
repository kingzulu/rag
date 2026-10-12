package com.zuluindustries.rag.core;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Führt mehrere Trefferlisten zu einer zusammen – "Reciprocal Rank Fusion" (RRF).
 *
 * <p>Jedes Dokument bekommt pro Liste {@code 1 / (k + Platz)} Punkte (Platz ab 1);
 * die Punkte aller Listen werden addiert. Gezählt wird nur der Platz, nicht der
 * Ähnlichkeitswert: Die Werte verschiedener Suchen sind nicht vergleichbar (z. B.
 * Frage mit Anweisung um 0,3, Absatz ohne Anweisung um 0,7), die Plätze schon.
 * Ein Dokument, das in mehreren Listen vorkommt, schlägt darum meist eines, das
 * nur in einer Liste weit vorn steht – Ausreißer einer einzelnen Suche glätten sich.
 *
 * <p>{@code k} dämpft den Vorsprung der vordersten Plätze; 60 ist der übliche Wert.
 */
public final class RankFusion {

    public static final int DEFAULT_K = 60;

    private RankFusion() {
    }

    /**
     * @return alle Dokumente der Listen, nach Punkten absteigend (bei Gleichstand in der
     *         Reihenfolge, in der sie zuerst vorkamen); als Wert die RRF-Punkte
     */
    public static List<SearchResult> fuse(List<List<SearchResult>> rankings, int k) {
        Map<String, Document> documents = new LinkedHashMap<>();
        Map<String, Double> points = new HashMap<>();
        for (List<SearchResult> ranking : rankings) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < ranking.size(); i++) {
                Document document = ranking.get(i).document();
                if (seen.add(document.id())) {   // pro Liste zählt nur der beste Platz
                    documents.putIfAbsent(document.id(), document);
                    points.merge(document.id(), 1.0 / (k + i + 1), Double::sum);
                }
            }
        }
        return documents.values().stream()
                .map(document -> new SearchResult(document, points.get(document.id())))
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .toList();
    }
}
