package com.zuluindustries.rag.app;

import java.nio.file.Path;
import java.util.List;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.DocumentSource;
import com.zuluindustries.rag.source.pdf.PdfDocumentSource;

/**
 * Einstiegspunkt der Anwendung. Liest vorerst nur ein PDF ein und zeigt,
 * was dabei herauskommt.
 *
 * <p>Optionales Programm-Argument: Pfad zur PDF-Datei. Ohne Argument wird das
 * Golfregel-PDF verwendet (Pfad relativ zum Projektordner rag-app, in dem
 * Eclipse das Programm startet).
 */
public class RagApp {

    private static final String DEFAULT_PDF = "../data/golfregeln/offizielle_golfregeln_2023.pdf";
    private static final int PREVIEW_PAGE = 41;
    private static final int PREVIEW_LENGTH = 1200;

    public static void main(String[] args) {
        Path pdfFile = Path.of(args.length > 0 ? args[0] : DEFAULT_PDF);

        DocumentSource source = new PdfDocumentSource(pdfFile);
        List<Document> documents = source.load();

        int totalChars = documents.stream().mapToInt(doc -> doc.text().length()).sum();
        System.out.println("Gelesen: " + pdfFile.getFileName());
        System.out.println("Seiten mit Text: " + documents.size());
        System.out.println("Zeichen gesamt: " + totalChars);

        documents.stream()
                .filter(doc -> String.valueOf(PREVIEW_PAGE).equals(doc.metadata().get("seite")))
                .findFirst()
                .ifPresent(doc -> {
                    System.out.println();
                    System.out.println("--- Seite " + PREVIEW_PAGE + " (Ausschnitt) ---");
                    String text = doc.text();
                    System.out.println(text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + " …" : text);
                });
    }
}
