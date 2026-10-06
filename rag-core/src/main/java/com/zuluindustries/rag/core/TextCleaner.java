package com.zuluindustries.rag.core;

import java.util.Optional;

/**
 * Bereinigt den Text eines Dokuments, z. B. indem Kopfzeilen entfernt oder
 * Silbentrennungen zusammengefügt werden. Mehrere Bereiniger lassen sich
 * hintereinanderschalten.
 */
public interface TextCleaner {

    /**
     * Bereinigt ein Dokument.
     *
     * @return das bereinigte Dokument, oder {@link Optional#empty()}, wenn nach
     *         dem Bereinigen kein Text mehr übrig ist
     */
    Optional<Document> clean(Document document);

    /**
     * Hilfsmethode für Umsetzungen: ersetzt den Text, oder liefert
     * {@link Optional#empty()}, wenn der neue Text leer ist.
     */
    static Optional<Document> replaceText(Document document, String newText) {
        return newText.isBlank() ? Optional.empty() : Optional.of(document.withText(newText));
    }
}
