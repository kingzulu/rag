package com.zuluindustries.rag.core;

import java.util.Optional;

/**
 * Übersetzt eine Frage vor der Suche in die Sprache des Dokuments – z. B. indem
 * Fachbegriffe ergänzt werden ("Kaninchenloch" → "Tierloch") oder ein Absatz im
 * Stil des Dokuments geschrieben wird. Der Suchtext dient nur der Suche;
 * beantwortet wird weiterhin die Originalfrage.
 */
@FunctionalInterface
public interface QueryRewriter {

    /** Sucht mit der unveränderten Frage. */
    QueryRewriter NONE = Optional::of;

    /**
     * @return der Text, mit dem gesucht werden soll – oder {@link Optional#empty()},
     *         wenn die Frage erkennbar nichts mit dem Dokument zu tun hat
     */
    Optional<String> rewrite(String question);
}
