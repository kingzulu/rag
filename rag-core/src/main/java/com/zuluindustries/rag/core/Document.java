package com.zuluindustries.rag.core;

import java.util.Map;

/**
 * Ein Dokument (oder ein Teil davon), wie es aus einer Quelle gelesen wurde.
 *
 * @param id       eindeutige Kennung, z. B. "golfregeln-2023#seite-41"
 * @param text     der reine Textinhalt
 * @param metadata zusätzliche Angaben wie Quelle oder Seitenzahl
 */
public record Document(String id, String text, Map<String, String> metadata) {

    public Document {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id darf nicht leer sein");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text darf nicht leer sein (id: " + id + ")");
        }
        // Unveränderliche Kopie: Niemand kann die Metadaten nachträglich von außen ändern.
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Document(String id, String text) {
        this(id, text, Map.of());
    }
}
