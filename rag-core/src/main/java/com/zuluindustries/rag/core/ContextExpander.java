package com.zuluindustries.rag.core;

import java.util.List;

/**
 * Ergänzt die Treffer einer Suche um weitere Dokumente, die das Sprachmodell
 * für eine vollständige Antwort braucht – z. B. die übrigen Abschnitte einer
 * Unterregel, wenn nur einer davon gefunden wurde.
 */
@FunctionalInterface
public interface ContextExpander {

    /** Lässt die Treffer unverändert. */
    ContextExpander NONE = hits -> hits;

    /**
     * @param hits die Treffer der Suche, der ähnlichste zuerst
     * @return die Quellen für das Sprachmodell (enthält alle Treffer)
     */
    List<SearchResult> expand(List<SearchResult> hits);

    /**
     * Schaltet einen weiteren Erweiterer dahinter: Er bekommt das Ergebnis dieses
     * Erweiterers. Beispiel: {@code siblings.andThen(definitions)}.
     */
    default ContextExpander andThen(ContextExpander next) {
        return hits -> next.expand(expand(hits));
    }
}
