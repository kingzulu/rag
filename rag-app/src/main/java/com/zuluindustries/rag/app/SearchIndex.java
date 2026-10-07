package com.zuluindustries.rag.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.zuluindustries.rag.store.memory.InMemoryVectorStore;

/**
 * Lädt den gespeicherten Suchindex aus {@link GolfRules#INDEX_FILE}.
 */
final class SearchIndex {

    private SearchIndex() {
    }

    /** Lädt den Index; bricht mit klarer Meldung ab, wenn er fehlt oder zu einem anderen Modell gehört. */
    static InMemoryVectorStore loadExisting() throws IOException {
        Path file = GolfRules.INDEX_FILE;
        if (!Files.exists(file)) {
            throw new IllegalStateException("Kein Suchindex gefunden (" + file.toAbsolutePath().normalize()
                    + "). Bitte zuerst IndexApp ausführen.");
        }
        InMemoryVectorStore store = InMemoryVectorStore.load(file);
        if (!store.model().equals(Scaleway.MODEL)) {
            throw new IllegalStateException("Der Suchindex wurde mit dem Modell " + store.model()
                    + " erstellt, eingestellt ist " + Scaleway.MODEL + ". Bitte IndexApp erneut ausführen.");
        }
        return store;
    }

    /**
     * Lädt den Index, wenn er existiert und zum eingestellten Modell passt;
     * sonst wird ein leerer Index angelegt.
     */
    static InMemoryVectorStore loadOrCreate() throws IOException {
        Path file = GolfRules.INDEX_FILE;
        if (!Files.exists(file)) {
            System.out.println("Noch kein Suchindex vorhanden – er wird neu aufgebaut.");
            return new InMemoryVectorStore(Scaleway.MODEL);
        }
        InMemoryVectorStore store = InMemoryVectorStore.load(file);
        if (!store.model().equals(Scaleway.MODEL)) {
            System.out.println("Suchindex gehört zum Modell " + store.model() + " – er wird für "
                    + Scaleway.MODEL + " neu aufgebaut.");
            return new InMemoryVectorStore(Scaleway.MODEL);
        }
        System.out.println("Vorhandener Suchindex geladen: " + store.size() + " Einträge.");
        return store;
    }
}
