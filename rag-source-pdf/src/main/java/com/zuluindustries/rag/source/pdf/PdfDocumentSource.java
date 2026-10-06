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
 * Liest eine PDF-Datei und liefert ein {@link Document} pro Seite.
 * Seiten ohne Text (Leerseiten, reine Bildseiten) werden übersprungen.
 * Der Text wird roh übernommen – das Aufräumen ist eine eigene Aufgabe.
 */
public class PdfDocumentSource implements DocumentSource {

    private final Path pdfFile;

    public PdfDocumentSource(Path pdfFile) {
        if (pdfFile == null || !Files.isRegularFile(pdfFile)) {
            throw new IllegalArgumentException("PDF-Datei nicht gefunden: "
                    + (pdfFile == null ? null : pdfFile.toAbsolutePath()));
        }
        this.pdfFile = pdfFile;
    }

    @Override
    public List<Document> load() {
        String fileName = pdfFile.getFileName().toString();
        String baseName = fileName.replaceFirst("(?i)\\.pdf$", "");

        try (PDDocument pdf = Loader.loadPDF(pdfFile.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<Document> documents = new ArrayList<>();

            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
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
