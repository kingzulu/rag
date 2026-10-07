package com.zuluindustries.rag.core;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Speichert Dokumente zusammen mit ihren Vektoren und findet zu einem
 * Anfrage-Vektor die ähnlichsten Dokumente.
 */
public interface VectorStore {

    /** Speichert ein Dokument mit seinem Vektor. Ein Eintrag mit derselben id wird ersetzt. */
    void add(Document document, float[] vector);

    /**
     * Liefert den gespeicherten Vektor, wenn ein Eintrag mit derselben id <b>und
     * demselben Text</b> existiert. Hat sich der Text geändert, ist der alte Vektor
     * nicht mehr gültig und es wird {@link Optional#empty()} geliefert.
     */
    Optional<float[]> vectorFor(Document document);

    /**
     * Entfernt alle Einträge, deren id nicht in der Liste steht.
     *
     * @return Anzahl der entfernten Einträge
     */
    int retainAll(Collection<String> ids);

    /** Die {@code topK} ähnlichsten Dokumente, das ähnlichste zuerst. */
    List<SearchResult> search(float[] query, int topK);

    int size();
}
