package com.zuluindustries.rag.core;

import java.util.List;

/**
 * Eine Quelle, aus der Dokumente gelesen werden – z. B. eine PDF-Datei,
 * eine Website oder ein Ordner. Wie die Quelle das macht, ist ihre Sache;
 * der Rest des Programms kennt nur dieses Interface.
 */
public interface DocumentSource {

    /**
     * Liest alle Dokumente der Quelle.
     *
     * @return die gelesenen Dokumente, nie {@code null}
     */
    List<Document> load();
}
