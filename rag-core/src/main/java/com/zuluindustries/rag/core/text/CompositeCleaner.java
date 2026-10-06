package com.zuluindustries.rag.core.text;

import java.util.List;
import java.util.Optional;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.TextCleaner;

/**
 * Führt mehrere Bereiniger nacheinander aus. Die Reihenfolge zählt: Jeder
 * Bereiniger bekommt das Ergebnis des vorherigen. Ist ein Dokument zwischendurch
 * leer, wird es nicht weiterverarbeitet.
 */
public class CompositeCleaner implements TextCleaner {

    private final List<TextCleaner> cleaners;

    public CompositeCleaner(TextCleaner... cleaners) {
        this.cleaners = List.of(cleaners);
    }

    @Override
    public Optional<Document> clean(Document document) {
        Optional<Document> result = Optional.of(document);
        for (TextCleaner cleaner : cleaners) {
            result = result.flatMap(cleaner::clean);
        }
        return result;
    }
}
