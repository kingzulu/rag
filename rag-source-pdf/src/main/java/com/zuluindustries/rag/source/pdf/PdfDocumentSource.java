package com.zuluindustries.rag.source.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.DocumentSource;

/**
 * Liest eine PDF-Datei und liefert ein {@link Document} pro Seite, optional nur
 * für einen Seitenbereich. Seiten ohne Text (Leerseiten, reine Bildseiten)
 * werden übersprungen.
 * Der Text wird roh übernommen – das Aufräumen ist eine eigene Aufgabe.
 */
public class PdfDocumentSource implements DocumentSource {

    private final Path pdfFile;
    private final int firstPage;
    private final int lastPage;
    private final boolean sortByPosition;

    /** Liest alle Seiten in der Reihenfolge, in der der Text in der Datei gespeichert ist. */
    public PdfDocumentSource(Path pdfFile) {
        this(pdfFile, 1, Integer.MAX_VALUE, false);
    }

    /**
     * @param firstPage      erste zu lesende Seite (ab 1)
     * @param lastPage       letzte zu lesende Seite (einschließlich)
     * @param sortByPosition {@code true}: Text nach seiner Position auf der Seite
     *                       sortieren (oben nach unten) statt in Speicherreihenfolge
     */
    public PdfDocumentSource(Path pdfFile, int firstPage, int lastPage, boolean sortByPosition) {
        if (pdfFile == null || !Files.isRegularFile(pdfFile)) {
            throw new IllegalArgumentException("PDF-Datei nicht gefunden: "
                    + (pdfFile == null ? null : pdfFile.toAbsolutePath()));
        }
        if (firstPage < 1 || lastPage < firstPage) {
            throw new IllegalArgumentException("Ungültiger Seitenbereich: " + firstPage + "–" + lastPage);
        }
        this.pdfFile = pdfFile;
        this.firstPage = firstPage;
        this.lastPage = lastPage;
        this.sortByPosition = sortByPosition;
    }

    @Override
    public List<Document> load() {
        String fileName = pdfFile.getFileName().toString();
        String baseName = fileName.replaceFirst("(?i)\\.pdf$", "");

        try (PDDocument pdf = Loader.loadPDF(pdfFile.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(sortByPosition);
            List<Document> documents = new ArrayList<>();

            int last = Math.min(lastPage, pdf.getNumberOfPages());
            for (int page = firstPage; page <= last; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf);

                if (text.isBlank()) {
                    continue;
                }
                documents.add(new Document(
                        baseName + "#seite-" + page,
                        text,
                        Map.of("quelle", fileName, "seite", String.valueOf(page))));
            }
            return documents;
        } catch (IOException e) {
            throw new UncheckedIOException("PDF konnte nicht gelesen werden: " + pdfFile, e);
        }
    }
}
