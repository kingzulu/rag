package com.zuluindustries.rag.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.DocumentSource;
import com.zuluindustries.rag.core.TextCleaner;
import com.zuluindustries.rag.core.text.CompositeCleaner;
import com.zuluindustries.rag.core.text.HyphenationCleaner;
import com.zuluindustries.rag.core.text.LinePatternCleaner;
import com.zuluindustries.rag.core.text.PageNumberCleaner;
import com.zuluindustries.rag.core.text.WhitespaceCleaner;
import com.zuluindustries.rag.source.pdf.PdfDocumentSource;

/**
 * Einstiegspunkt der Anwendung. Liest das PDF ein, bereinigt den Text und
 * schreibt Roh- und bereinigten Text in Dateien neben dem PDF, damit man das
 * Ergebnis prüfen kann.
 *
 * <p>Optionales Programm-Argument: Pfad zur PDF-Datei. Ohne Argument wird das
 * Golfregel-PDF verwendet (Pfad relativ zum Projektordner rag-app, in dem
 * Eclipse das Programm startet).
 */
public class RagApp {

    private static final String DEFAULT_PDF = "../data/golfregeln/offizielle_golfregeln_2023.pdf";
    private static final String RAW_TEXT_FILE = "rohtext-pdfbox-sortiert.txt";
    private static final String CLEAN_TEXT_FILE = "text-bereinigt.txt";

    // Golfregel-spezifische Einstellungen (wandern später in eine YAML-Konfiguration)
    private static final int FIRST_PAGE = 20;   // "Wesentliche Änderungen 2023"
    private static final int LAST_PAGE = 268;   // letzte Seite der Definitionen
    private static final boolean SORT_BY_POSITION = true;
    private static final int PRINTED_PAGE_OFFSET = -2;
    private static final String[] HEADER_PATTERNS = {
            "Regel (\\d{1,3}|x)",       // Kopfzeile "Regel 3"; auf Definitionsseiten "Regel x";
                                        // "Regel 156" = Kopfzeile "Regel 15" + große "6" aus "16"
            "REGEL",                    // Titelseiten der Regeln
            "De?\\s?finitionen",        // Kopfzeile "Definitionen", teils als "D finitionen"
            "RDegfienli txionen",       // "Regel x" und "Definitionen" durch Sortierung ineinander geschoben
    };

    public static void main(String[] args) throws IOException {
        Path pdfFile = Path.of(args.length > 0 ? args[0] : DEFAULT_PDF);

        DocumentSource source = new PdfDocumentSource(pdfFile, FIRST_PAGE, LAST_PAGE, SORT_BY_POSITION);
        TextCleaner cleaner = new CompositeCleaner(
                new PageNumberCleaner(PRINTED_PAGE_OFFSET),
                new LinePatternCleaner(HEADER_PATTERNS),
                new HyphenationCleaner(),
                new WhitespaceCleaner());

        List<Document> rawDocuments = source.load();
        List<Document> cleanDocuments = rawDocuments.stream()
                .map(cleaner::clean)
                .flatMap(Optional::stream)
                .toList();

        System.out.println("Gelesen: " + pdfFile.getFileName() + ", Seiten " + FIRST_PAGE + "–" + LAST_PAGE);
        System.out.println("Seiten mit Text: " + rawDocuments.size() + " roh, " + cleanDocuments.size() + " bereinigt");
        System.out.println("Zeichen gesamt: " + countChars(rawDocuments) + " roh, " + countChars(cleanDocuments) + " bereinigt");

        writeText(rawDocuments, pdfFile.resolveSibling(RAW_TEXT_FILE));
        writeText(cleanDocuments, pdfFile.resolveSibling(CLEAN_TEXT_FILE));
        System.out.println("Dateien geschrieben in: " + pdfFile.toAbsolutePath().normalize().getParent());
    }

    private static int countChars(List<Document> documents) {
        return documents.stream().mapToInt(doc -> doc.text().length()).sum();
    }

    /** Schreibt alle Dokumente untereinander in eine Datei, jeweils mit einer Trennzeile. */
    private static void writeText(List<Document> documents, Path file) throws IOException {
        StringBuilder out = new StringBuilder();
        for (Document doc : documents) {
            out.append("===== PDF-Seite ").append(doc.metadata().get("seite"));
            String printedPage = doc.metadata().get(PageNumberCleaner.PRINTED_PAGE_KEY);
            if (printedPage != null) {
                out.append(" (gedruckt: ").append(printedPage).append(')');
            }
            out.append(" =====\n");
            out.append(doc.text()).append('\n');
        }
        Files.writeString(file, out);
    }
}
