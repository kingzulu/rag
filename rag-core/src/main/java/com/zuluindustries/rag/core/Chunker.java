package com.zuluindustries.rag.core;

import java.util.List;

/**
 * Zerlegt Seiten in Abschnitte ("Chunks"), die später einzeln in Embeddings
 * umgewandelt und gesucht werden. Ein Chunker bekommt alle Seiten eines
 * Bereichs auf einmal, weil Abschnitte über Seitengrenzen hinweg laufen können.
 */
public interface Chunker {

    /**
     * @param pages die (bereinigten) Seiten in Lesereihenfolge
     * @return die Chunks – jeweils ein eigenes Dokument mit Metadaten
     */
    List<Document> chunk(List<Document> pages);
}
