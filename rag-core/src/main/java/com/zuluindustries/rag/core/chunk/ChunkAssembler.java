package com.zuluindustries.rag.core.chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.zuluindustries.rag.core.Document;

/**
 * Baut aus einer Überschrift und den zugehörigen Zeilen fertige Chunks.
 * Wird von allen Chunkern benutzt, damit Chunks überall gleich aussehen:
 * <ul>
 * <li>Der Text beginnt mit der Überschriften-Kette, dann folgt eine Leerzeile
 * und der Inhalt.</li>
 * <li>Zu lange Inhalte werden geteilt, bevorzugt vor Absatzmarken wie "(1)"
 * oder "•". Jeder Teil bekommt die Überschriften-Kette erneut.</li>
 * <li>Metadaten: Quelle, Abschnitt, Überschrift, Nummer, Seite.</li>
 * </ul>
 */
public class ChunkAssembler {

    public static final String SOURCE_KEY = "quelle";
    public static final String SECTION_KEY = "abschnitt";
    public static final String HEADING_KEY = "ueberschrift";
    public static final String NUMBER_KEY = "nummer";
    public static final String PAGE_KEY = "seite";
    public static final String PRINTED_PAGE_KEY = "seite_gedruckt";

    /** Zeilen, vor denen sich ein Schnitt anbietet: "(1)", "•", "»", "Ausnahme", "Strafe für". */
    private static final Pattern PARAGRAPH_START = Pattern.compile("(\\(\\d+\\)|•|»|Ausnahme|Strafe für).*");

    private final String sectionName;
    private final int maxChars;
    private final List<Document> chunks = new ArrayList<>();

    /**
     * @param sectionName Name des Abschnitts, z. B. "Regeln"
     * @param maxChars    gewünschte Höchstlänge des Inhalts eines Chunks (ohne Überschrift)
     */
    public ChunkAssembler(String sectionName, int maxChars) {
        this.sectionName = sectionName;
        this.maxChars = maxChars;
    }

    /**
     * Fügt einen Abschnitt hinzu. Leere Abschnitte werden ignoriert.
     *
     * @param heading die Überschriften-Kette, z. B. "Regel 3 – Das Turnier › 3.3 Zählspiel"
     * @param number  die Nummer des Abschnitts, z. B. "3.3"; darf {@code null} sein
     * @param body    die Zeilen des Abschnitts
     */
    public void add(String heading, String number, List<Line> body) {
        List<Line> content = body.stream().filter(line -> !line.text().isBlank()).toList();
        if (content.isEmpty()) {
            return;
        }
        for (List<Line> part : split(content)) {
            chunks.add(toChunk(heading, number, part));
        }
    }

    public List<Document> build() {
        return List.copyOf(chunks);
    }

    /** Teilt den Inhalt in Teile von höchstens etwa {@code maxChars} Zeichen. */
    private List<List<Line>> split(List<Line> content) {
        int total = content.stream().mapToInt(line -> line.text().length() + 1).sum();
        if (total <= maxChars) {
            return List.of(content);
        }
        List<List<Line>> parts = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        int size = 0;
        for (Line line : content) {
            int length = line.text().length() + 1;
            boolean full = size + length > maxChars;
            boolean goodBreak = size > maxChars / 2 && PARAGRAPH_START.matcher(line.text()).matches();
            if (!current.isEmpty() && (full || goodBreak)) {
                parts.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(line);
            size += length;
        }
        parts.add(current);
        return parts;
    }

    private Document toChunk(String heading, String number, List<Line> part) {
        Document firstPage = part.getFirst().page();
        String text = heading + "\n\n" + part.stream().map(Line::text).collect(Collectors.joining("\n"));

        Map<String, String> metadata = new HashMap<>();
        metadata.put(SECTION_KEY, sectionName);
        metadata.put(HEADING_KEY, heading);
        if (number != null) {
            metadata.put(NUMBER_KEY, number);
        }
        for (String key : List.of(SOURCE_KEY, PAGE_KEY, PRINTED_PAGE_KEY)) {
            String value = firstPage.metadata().get(key);
            if (value != null) {
                metadata.put(key, value);
            }
        }

        String id = baseId(firstPage) + "#" + slug(sectionName) + "-" + (chunks.size() + 1);
        return new Document(id, text, metadata);
    }

    /** "offizielle_golfregeln_2023#seite-41" wird zu "offizielle_golfregeln_2023". */
    private static String baseId(Document page) {
        int hash = page.id().indexOf('#');
        return hash < 0 ? page.id() : page.id().substring(0, hash);
    }

    /** "Wesentliche Änderungen 2023" wird zu "wesentliche-aenderungen-2023". */
    static String slug(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
