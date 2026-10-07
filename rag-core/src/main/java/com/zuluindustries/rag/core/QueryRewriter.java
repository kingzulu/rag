package com.zuluindustries.rag.core;

/**
 * Formt eine Frage vor der Suche um, damit sie besser zu den Formulierungen im
 * Dokument passt – z. B. indem Fachbegriffe ergänzt werden ("Kaninchenloch"
 * → "Tierloch"). Der umgeformte Text dient nur der Suche; beantwortet wird
 * weiterhin die Originalfrage.
 */
@FunctionalInterface
public interface QueryRewriter {

    /** Lässt die Frage unverändert. */
    QueryRewriter NONE = question -> question;

    /** @return der Text, mit dem gesucht werden soll */
    String rewrite(String question);
}
