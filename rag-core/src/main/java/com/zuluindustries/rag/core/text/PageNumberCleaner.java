package com.zuluindustries.rag.core.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.TextCleaner;

/**
 * Findet die gedruckte Seitenzahl, entfernt sie aus dem Text und speichert sie
 * als Metadatum {@value #PRINTED_PAGE_KEY}.
 *
 * <p>Die gedruckte Seitenzahl weicht oft von der Seite in der PDF-Datei ab
 * (Titelseiten usw. haben keine Nummer). Der Versatz wird angegeben: Ist
 * PDF-Seite 41 im Buch Seite 39, ist der Versatz -2. Gesucht wird eine Zeile,
 * die nur aus dieser Zahl besteht – bevorzugt in den ersten oder letzten Zeilen
 * der Seite, notfalls irgendwo auf der Seite.
 */
public class PageNumberCleaner implements TextCleaner {

    public static final String PDF_PAGE_KEY = "seite";
    public static final String PRINTED_PAGE_KEY = "seite_gedruckt";

    private static final int SEARCH_LINES = 3;

    private final int offset;

    public PageNumberCleaner(int offset) {
        this.offset = offset;
    }

    @Override
    public Optional<Document> clean(Document document) {
        String pdfPage = document.metadata().get(PDF_PAGE_KEY);
        if (pdfPage == null || !pdfPage.matches("\\d+")) {
            return Optional.of(document);
        }
        String expected = String.valueOf(Integer.parseInt(pdfPage) + offset);

        List<String> lines = new ArrayList<>(Arrays.asList(document.text().split("\n", -1)));
        int index = findLine(lines, expected);
        if (index < 0) {
            return Optional.of(document);
        }
        lines.remove(index);
        return TextCleaner.replaceText(document, String.join("\n", lines))
                .map(doc -> doc.withMetadata(PRINTED_PAGE_KEY, expected));
    }

    /**
     * Sucht die Zahl zuerst in den ersten und letzten Zeilen, wo Seitenzahlen
     * normalerweise stehen. Nur wenn sie dort fehlt, wird die ganze Seite
     * durchsucht (z. B. wenn eine Abbildung die Textreihenfolge verschiebt).
     *
     * @return Index der Zeile, oder -1, wenn die Zahl nicht vorkommt
     */
    private static int findLine(List<String> lines, String expected) {
        int fallback = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).strip().equals(expected)) {
                continue;
            }
            boolean nearStart = i < SEARCH_LINES;
            boolean nearEnd = i >= lines.size() - SEARCH_LINES;
            if (nearStart || nearEnd) {
                return i;
            }
            if (fallback < 0) {
                fallback = i;
            }
        }
        return fallback;
    }
}
