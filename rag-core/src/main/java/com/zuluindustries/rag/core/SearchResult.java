package com.zuluindustries.rag.core;

/**
 * Ein Treffer einer Suche: das gefundene Dokument und wie ähnlich es der
 * Anfrage ist (Kosinus-Ähnlichkeit, höher = ähnlicher).
 */
public record SearchResult(Document document, double score) {
}
