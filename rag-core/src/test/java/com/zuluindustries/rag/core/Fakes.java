package com.zuluindustries.rag.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Einfache Attrappen für Tests, die ein EmbeddingModel oder einen VectorStore brauchen. */
final class Fakes {

    private Fakes() {
    }

    /**
     * Embedding-Modell ohne Internet: Der "Vektor" eines Textes zählt, wie oft
     * die Wörter "Bunker", "Grün" und "Ball" darin vorkommen. Merkt sich alle
     * Texte, die eingebettet wurden.
     */
    static class WordCountEmbeddingModel implements EmbeddingModel {

        final List<String> embeddedTexts = new ArrayList<>();

        @Override
        public List<float[]> embed(List<String> texts) {
            embeddedTexts.addAll(texts);
            return texts.stream()
                    .map(text -> new float[] { count(text, "Bunker"), count(text, "Grün"), count(text, "Ball") })
                    .toList();
        }

        private static float count(String text, String word) {
            return text.split(word, -1).length - 1;
        }
    }

    /** Vektorspeicher in einer Map – genug für Tests. */
    static class MapVectorStore implements VectorStore {

        private record Entry(Document document, float[] vector) {
        }

        private final Map<String, Entry> entries = new LinkedHashMap<>();

        @Override
        public void add(Document document, float[] vector) {
            entries.put(document.id(), new Entry(document, vector));
        }

        @Override
        public Optional<float[]> vectorFor(Document document) {
            Entry entry = entries.get(document.id());
            return entry != null && entry.document().text().equals(document.text())
                    ? Optional.of(entry.vector())
                    : Optional.empty();
        }

        @Override
        public int retainAll(Collection<String> ids) {
            int before = entries.size();
            entries.keySet().retainAll(ids);
            return before - entries.size();
        }

        @Override
        public List<SearchResult> search(float[] query, int topK) {
            return entries.values().stream()
                    .map(entry -> new SearchResult(entry.document(), VectorMath.cosineSimilarity(query, entry.vector())))
                    .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                    .limit(topK)
                    .toList();
        }

        @Override
        public int size() {
            return entries.size();
        }
    }
}
