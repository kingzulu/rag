package com.zuluindustries.rag.app;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.zuluindustries.rag.core.Chunker;
import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.DocumentSource;
import com.zuluindustries.rag.core.TextCleaner;
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
 * Alles, was speziell für das Golfregel-PDF gilt: Seitenbereiche, Kopfzeilen,
 * Regeltitel und wie die einzelnen Teile des Buchs zerlegt werden. Dazu der
 * Ablauf "PDF lesen → bereinigen → in Chunks zerlegen".
 *
 * <p>Diese Einstellungen wandern später in eine YAML-Konfiguration.
 */
public final class GolfRules {

    public static final Path DEFAULT_PDF = Path.of("../data/golfregeln/offizielle_golfregeln_2023.pdf");
    public static final Path INDEX_FILE = DEFAULT_PDF.resolveSibling("suchindex.jsonl");

    public static final int FIRST_PAGE = 20;    // "Wesentliche Änderungen 2023"
    public static final int LAST_PAGE = 268;    // letzte Seite der Definitionen
    public static final int MAX_CHUNK_CHARS = 2000;

    /** So viele Treffer liefert die Suche für eine Antwort. */
    public static final int ANSWER_TOP_K = 5;
    /** Obergrenzen, wenn die Treffer um Geschwister-Abschnitte ergänzt werden. */
    public static final int MAX_CONTEXT_CHUNKS = 10;
    public static final int MAX_CONTEXT_CHARS = 12_000;

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

    /**
     * Antwort, wenn die Golfregeln nichts zur Frage enthalten – vom Sprachmodell
     * (laut Systemanweisung) oder direkt, wenn kein Suchtreffer die Schwelle erreicht.
     */
    public static final String NO_ANSWER_TEXT = "Dazu finde ich in den Golfregeln keine Antwort.";

    /**
     * Verhaltensregeln für das Sprachmodell, wenn es Fragen zu den Golfregeln beantwortet (V5).
     *
     * <p>Entwicklung: V1 war zu streng (verweigerte differenzierte Antworten wie
     * beim Bunker). V2 erlaubte Teilantworten, aber "sage, was fehlt" führte zu
     * erfundenen Lücken in fast jeder Antwort. V3 beschränkt das auf wesentliche
     * Lücken und verlangt Regelnummern zusammen mit der Quelle. V4 sollte
     * mehrdeutige Fragen klären ("Abschlag"), stellte aber fast jeder Antwort einen
     * Deutungssatz voran, verschlechterte mehrere Antworten und öffnete eine
     * Hintertür für eigenes Wissen – verworfen. V5 = V3 plus nur das Verbot, den
     * Standardsatz zusätzlich an eine Antwort zu hängen. Frühere Fassungen stehen
     * in der Git-Historie.
     *
     * <p>Grenzen (Messung 7.10.2026): V5 verhindert den angehängten Standardsatz
     * nicht zuverlässig – das Modell variiert ihn ("Dazu findest du …"). Die
     * Schwankung zwischen zwei Läufen derselben Fassung war so groß wie der
     * Unterschied zwischen V3 und V5; kleine Prompt-Änderungen lassen sich mit
     * einem einzelnen Lauf nicht sicher bewerten.
     */
    public static final String SYSTEM_PROMPT = """
            Du beantwortest Fragen zu den Offiziellen Golfregeln (gültig ab Januar 2023).

            So gehst du vor:
            - Nutze ausschließlich die nummerierten Quellen in der Nachricht. Schreibe nichts, was dort nicht \
            steht – auch wenn du es zu wissen glaubst. Lieber unvollständig als unbelegt.
            - Belege jede Aussage mit der Quelle, in der sie tatsächlich steht, z. B. [1] oder [2][3].
            - Nenne Regelnummern immer zusammen mit ihrer Quelle, z. B. "Regel 14.1b [1]".
            - Hängt die Antwort von den Umständen ab, erkläre die Fälle (z. B. "verboten, wenn …; erlaubt, \
            wenn …").
            - Fehlt ein für die Frage wesentlicher Teil in den Quellen, sage das in einem kurzen Satz. Mach \
            keine Aussagen über Dinge, nach denen nicht gefragt wurde.
            - Ignoriere Quellen, die nichts zur Frage beitragen.
            - Nur wenn keine Quelle zur Frage passt, antworte genau: "%s" \
            Verwende diesen Satz nie zusätzlich zu einer Antwort.
            - Antworte auf Deutsch, knapp und verständlich (höchstens etwa 150 Wörter).
            """.formatted(NO_ANSWER_TEXT);

    /** Ein Bereich des Buchs mit eigener Zerlege-Strategie. */
    public record Section(String name, int firstPage, int lastPage, Chunker chunker) {
    }

    public static final List<Section> SECTIONS = List.of(
            new Section("Wesentliche Änderungen 2023", 20, 21,
                    new SizeChunker("Wesentliche Änderungen 2023", MAX_CHUNK_CHARS)),
            new Section("Wie man das Regelbuch benutzt", 22, 23,
                    new SizeChunker("Wie man das Regelbuch benutzt", MAX_CHUNK_CHARS)),
            new Section("Regeln", 24, 240,
                    new HeadingChunker("Regeln", MAX_CHUNK_CHARS, "Regel", RULE_TITLES, "Zweck der Regel:", 6)),
            new Section("Definitionen", 241, 268,
                    new TermChunker("Definitionen", MAX_CHUNK_CHARS, 45, 6)));

    /** Ergebnis der Verarbeitung: Seiten vor und nach dem Bereinigen sowie die Chunks je Abschnitt. */
    public record Result(List<Document> rawPages, List<Document> cleanPages,
            Map<Section, List<Document>> chunksBySection) {

        public List<Document> chunks() {
            return chunksBySection.values().stream().flatMap(List::stream).toList();
        }
    }

    private GolfRules() {
    }

    /** Liest das PDF, bereinigt den Text und zerlegt ihn in Chunks. */
    public static Result process(Path pdfFile) {
        DocumentSource source = new PdfDocumentSource(pdfFile, FIRST_PAGE, LAST_PAGE, SORT_BY_POSITION);
        TextCleaner cleaner = new CompositeCleaner(
                new PageNumberCleaner(PRINTED_PAGE_OFFSET),
                new LinePatternCleaner(HEADER_PATTERNS),
                new HyphenationCleaner(),
                new WhitespaceCleaner());

        List<Document> rawPages = source.load();
        List<Document> cleanPages = rawPages.stream()
                .map(cleaner::clean)
                .flatMap(Optional::stream)
                .toList();

        Map<Section, List<Document>> chunksBySection = new LinkedHashMap<>();
        for (Section section : SECTIONS) {
            List<Document> sectionPages = cleanPages.stream()
                    .filter(page -> isInSection(page, section))
                    .toList();
            chunksBySection.put(section, section.chunker().chunk(sectionPages));
        }
        return new Result(rawPages, cleanPages, chunksBySection);
    }

    private static boolean isInSection(Document page, Section section) {
        int pageNumber = Integer.parseInt(page.metadata().get(PageNumberCleaner.PDF_PAGE_KEY));
        return pageNumber >= section.firstPage()
                && pageNumber <= section.lastPage()
                && !SKIPPED_PAGES.contains(pageNumber);
    }
}
