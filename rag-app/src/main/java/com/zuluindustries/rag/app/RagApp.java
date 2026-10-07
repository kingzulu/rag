package com.zuluindustries.rag.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.chunk.ChunkAssembler;
import com.zuluindustries.rag.core.text.PageNumberCleaner;

/**
 * Verarbeitet das Golfregel-PDF und schreibt die Zwischenergebnisse (Rohtext,
 * bereinigter Text, Chunks) in Dateien neben dem PDF, damit man sie prüfen kann.
 *
 * <p>Optionales Programm-Argument: Pfad zur PDF-Datei. Ohne Argument wird das
 * Golfregel-PDF verwendet (Pfad relativ zum Projektordner rag-app, in dem
 * Eclipse das Programm startet).
 */
public class RagApp {

    private static final String RAW_TEXT_FILE = "rohtext-pdfbox-sortiert.txt";
    private static final String CLEAN_TEXT_FILE = "text-bereinigt.txt";
    private static final String CHUNKS_FILE = "chunks.txt";

    public static void main(String[] args) throws IOException {
        Path pdfFile = args.length > 0 ? Path.of(args[0]) : GolfRules.DEFAULT_PDF;

        GolfRules.Result result = GolfRules.process(pdfFile);

        System.out.println("Gelesen: " + pdfFile.getFileName()
                + ", Seiten " + GolfRules.FIRST_PAGE + "–" + GolfRules.LAST_PAGE);
        System.out.println("Seiten mit Text: " + result.rawPages().size() + " roh, "
                + result.cleanPages().size() + " bereinigt");
        result.chunksBySection().forEach((section, chunks) -> System.out.printf(
                "  %-32s Seiten %3d–%3d: %3d Chunks%n",
                section.name(), section.firstPage(), section.lastPage(), chunks.size()));
        printStatistics(result.chunks());

        writePages(result.rawPages(), pdfFile.resolveSibling(RAW_TEXT_FILE));
        writePages(result.cleanPages(), pdfFile.resolveSibling(CLEAN_TEXT_FILE));
        writeChunks(result.chunks(), pdfFile.resolveSibling(CHUNKS_FILE));
        System.out.println("Dateien geschrieben in: " + pdfFile.toAbsolutePath().normalize().getParent());
    }

    private static void printStatistics(List<Document> chunks) {
        List<Integer> lengths = chunks.stream().map(chunk -> chunk.text().length()).sorted().toList();
        long tooLong = lengths.stream().filter(length -> length > GolfRules.MAX_CHUNK_CHARS).count();
        System.out.println("Chunks gesamt: " + chunks.size());
        System.out.println("Länge in Zeichen: kürzester " + lengths.getFirst()
                + ", Median " + lengths.get(lengths.size() / 2)
                + ", längster " + lengths.getLast()
                + " (über " + GolfRules.MAX_CHUNK_CHARS + " inkl. Überschrift: " + tooLong + ")");
    }

    /** Schreibt Seiten untereinander in eine Datei, jeweils mit einer Trennzeile. */
    private static void writePages(List<Document> pages, Path file) throws IOException {
        StringBuilder out = new StringBuilder();
        for (Document page : pages) {
            out.append("===== PDF-Seite ").append(page.metadata().get(PageNumberCleaner.PDF_PAGE_KEY));
            String printedPage = page.metadata().get(PageNumberCleaner.PRINTED_PAGE_KEY);
            if (printedPage != null) {
                out.append(" (gedruckt: ").append(printedPage).append(')');
            }
            out.append(" =====\n");
            out.append(page.text()).append('\n');
        }
        Files.writeString(file, out);
    }

    /** Schreibt alle Chunks mit Kennung, Seite und Länge in eine Datei. */
    private static void writeChunks(List<Document> chunks, Path file) throws IOException {
        StringBuilder out = new StringBuilder();
        for (Document chunk : chunks) {
            out.append("===== ").append(chunk.id())
                    .append(" | Seite ").append(chunk.metadata().get(ChunkAssembler.PAGE_KEY))
                    .append(" (gedruckt: ").append(chunk.metadata().getOrDefault(ChunkAssembler.PRINTED_PAGE_KEY, "–"))
                    .append(") | ").append(chunk.text().length()).append(" Zeichen =====\n");
            out.append(chunk.text()).append("\n\n");
        }
        Files.writeString(file, out);
    }
}
