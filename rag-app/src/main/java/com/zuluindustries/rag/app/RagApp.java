package com.zuluindustries.rag.app;

import java.util.Map;

import com.zuluindustries.rag.core.Document;

/**
 * Einstiegspunkt der Anwendung. Vorerst nur ein Lebenszeichen, das zeigt,
 * dass rag-app das Modul rag-core korrekt einbindet.
 */
public class RagApp {

    public static void main(String[] args) {
        Document doc = new Document(
                "golfregeln-2023#seite-41",
                "3.3a Gewinner im Zählspiel: Der Spieler mit dem niedrigsten Gesamtergebnis gewinnt.",
                Map.of("quelle", "offizielle_golfregeln_2023.pdf", "seite", "41"));

        System.out.println("RAG-App läuft (Java " + Runtime.version() + ")");
        System.out.println(doc);
    }
}
