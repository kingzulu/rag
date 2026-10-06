package com.zuluindustries.rag.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.zuluindustries.rag.core.Chunker;
import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.DocumentSource;
import com.zuluindustries.rag.core.TextCleaner;
import com.zuluindustries.rag.core.chunk.ChunkAssembler;
import com.zuluindustries.rag.core.chunk.HeadingChunker;
import com.zuluindustries.rag.core.chunk.SizeChunker;
import com.zuluindustries.rag.core.chunk.TermChunker;
import com.zuluindustries.rag.core.text.CompositeCleaner;
import com.zuluindustries.rag.core.text.HyphenationCleaner;
import com.zuluindustries.rag.core.text.LinePatternCleaner;
import com.zuluindustries.rag.core.text.PageNumberCleaner;
import com.zuluindustries.rag.core.text.WhitespaceCleaner;
import com.zuluindustries.rag.source.pdf.PdfDocumentSource;

/**
 * Einstiegspunkt der Anwendung. Liest das PDF ein, bereinigt den Text,
 * zerlegt ihn in Chunks und schreibt Zwischenergebnisse in Dateien neben dem
 * PDF, damit man sie prüfen kann.
 *
 * <p>Optionales Programm-Argument: Pfad zur PDF-Datei. Ohne Argument wird das
 * Golfregel-PDF verwendet (Pfad relativ zum Projektordner rag-app, in dem
 * Eclipse das Programm startet).
 */
public class RagApp {

    private static final String DEFAULT_PDF = "../data/golfregeln/offizielle_golfregeln_2023.pdf";
    private static final String RAW_TEXT_FILE = "rohtext-pdfbox-sortiert.txt";
    private static final String CLEAN_TEXT_FILE = "text-bereinigt.txt";
    private static final String CHUNKS_FILE = "chunks.txt";

    // ---- Golfregel-spezifische Einstellungen (wandern später in eine YAML-Konfiguration) ----

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
            "DefinRiteiogneel nx",      // dasselbe in anderer Mischung
            "Wesentliche Änderungen",   // Kopfzeile der Seiten 20–21
            "Wie man das Regelbuch benutzt", // Kopfzeile der Seiten 22–23
    };

    /** Trennseiten der Teile I–XI: nur Teilnummer und Teiltitel, kein Regeltext. */
    private static final Set<Integer> SKIPPED_PAGES = Set.of(25, 57, 79, 113, 129, 149, 171, 194, 195, 205, 229, 241);

    private static final int MAX_CHUNK_CHARS = 2000;

    /** Titel der 25 Regeln – im PDF durch das Layout zu zerstückelt, um sie zuverlässig auszulesen. */
    private static final Map<Integer, String> RULE_TITLES = Map.ofEntries(
            Map.entry(1, "Das Spiel, Verhalten der Spieler und die Regeln"),
            Map.entry(2, "Der Platz"),
            Map.entry(3, "Das Turnier"),
            Map.entry(4, "Ausrüstung des Spielers"),
            Map.entry(5, "Spielen der Runde"),
            Map.entry(6, "Spielen eines Lochs"),
            Map.entry(7, "Ballsuche: Ball finden und identifizieren"),
            Map.entry(8, "Den Platz spielen, wie er vorgefunden wird"),
            Map.entry(9, "Ball spielen, wie er liegt; Ball in Ruhe aufgenommen oder bewegt"),
            Map.entry(10, "Auf Schlag vorbereiten und ausführen; Beratung und Hilfe; Caddies"),
            Map.entry(11, "Ball in Bewegung trifft versehentlich Person, Tier oder Gegenstand; "
                    + "absichtliche Handlungen, um Ball in Bewegung zu beeinflussen"),
            Map.entry(12, "Bunker"),
            Map.entry(13, "Grüns"),
            Map.entry(14, "Verfahren für den Ball: Markieren, Aufnehmen und Reinigen; an Stelle zurücklegen; "
                    + "Droppen im Erleichterungsbereich; Spielen vom falschen Ort"),
            Map.entry(15, "Erleichterung von losen hinderlichen Naturstoffen und beweglichen Hemmnissen "
                    + "(einschließlich Ball oder Ballmarker, die das Spiel unterstützen oder beeinträchtigen)"),
            Map.entry(16, "Erleichterung von ungewöhnlichen Platzverhältnissen (einschließlich unbeweglicher "
                    + "Hemmnisse), Gefährdung durch Tiere, eingebetteter Ball"),
            Map.entry(17, "Penalty Areas"),
            Map.entry(18, "Erleichterung mit Strafe von Schlag und Distanzverlust, Ball verloren oder Aus, "
                    + "provisorischer Ball"),
            Map.entry(19, "Ball unspielbar"),
            Map.entry(20, "Entscheiden von Regelfällen während der Runde; Regelentscheidungen eines Referees "
                    + "und der Spielleitung"),
            Map.entry(21, "Andere Formen des Einzel-Zählspiels und -Lochspiels"),
            Map.entry(22, "Vierer"),
            Map.entry(23, "Vierball"),
            Map.entry(24, "Mannschaftsturniere"),
            Map.entry(25, "Anpassungen für Spieler mit Behinderungen"));

    /** Ein Bereich des Buchs mit eigener Zerlege-Strategie. */
    private record Section(String name, int firstPage, int lastPage, Chunker chunker) {
    }

    private static final List<Section> SECTIONS = List.of(
            new Section("Wesentliche Änderungen 2023", 20, 21,
                    new SizeChunker("Wesentliche Änderungen 2023", MAX_CHUNK_CHARS)),
            new Section("Wie man das Regelbuch benutzt", 22, 23,
                    new SizeChunker("Wie man das Regelbuch benutzt", MAX_CHUNK_CHARS)),
            new Section("Regeln", 24, 240,
                    new HeadingChunker("Regeln", MAX_CHUNK_CHARS, "Regel", RULE_TITLES, "Zweck der Regel:", 6)),
            new Section("Definitionen", 241, 268,
                    new TermChunker("Definitionen", MAX_CHUNK_CHARS, 45, 6)));

    // ---- Programm ----

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

        List<Document> chunks = new ArrayList<>();
        for (Section section : SECTIONS) {
            List<Document> sectionPages = cleanDocuments.stream()
                    .filter(page -> isInSection(page, section))
                    .toList();
            List<Document> sectionChunks = section.chunker().chunk(sectionPages);
            System.out.printf("  %-32s Seiten %3d–%3d: %3d Chunks%n",
                    section.name(), section.firstPage(), section.lastPage(), sectionChunks.size());
            chunks.addAll(sectionChunks);
        }
        printStatistics(chunks);

        writePages(rawDocuments, pdfFile.resolveSibling(RAW_TEXT_FILE));
        writePages(cleanDocuments, pdfFile.resolveSibling(CLEAN_TEXT_FILE));
        writeChunks(chunks, pdfFile.resolveSibling(CHUNKS_FILE));
        System.out.println("Dateien geschrieben in: " + pdfFile.toAbsolutePath().normalize().getParent());
    }

    private static boolean isInSection(Document page, Section section) {
        int pageNumber = Integer.parseInt(page.metadata().get(PageNumberCleaner.PDF_PAGE_KEY));
        return pageNumber >= section.firstPage()
                && pageNumber <= section.lastPage()
                && !SKIPPED_PAGES.contains(pageNumber);
    }

    private static void printStatistics(List<Document> chunks) {
        List<Integer> lengths = chunks.stream().map(chunk -> chunk.text().length()).sorted().toList();
        long tooLong = lengths.stream().filter(length -> length > MAX_CHUNK_CHARS).count();
        System.out.println("Chunks gesamt: " + chunks.size());
        System.out.println("Länge in Zeichen: kürzester " + lengths.getFirst()
                + ", Median " + lengths.get(lengths.size() / 2)
                + ", längster " + lengths.getLast()
                + " (über " + MAX_CHUNK_CHARS + " inkl. Überschrift: " + tooLong + ")");
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
