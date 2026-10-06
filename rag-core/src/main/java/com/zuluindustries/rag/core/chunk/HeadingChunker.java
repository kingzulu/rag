package com.zuluindustries.rag.core.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zuluindustries.rag.core.Chunker;
import com.zuluindustries.rag.core.Document;

/**
 * Zerlegt nummerierte Texte (wie Regelwerke) an ihren Überschriften.
 *
 * <p>Drei Ebenen werden unterschieden:
 * <ol>
 * <li><b>Kapitel</b>, z. B. "Regel 3". Ein Kapitel beginnt dort, wo eine
 * Markierungszeile (z. B. "Zweck der Regel:") weit oben auf einer Seite steht,
 * ohne dass auf dieser Seite schon eine Überschrift kam. Was auf der Seite davor
 * steht (der oft zerstückelte Kapiteltitel), wird verworfen; die Titel kommen
 * stattdessen aus einer festen Liste. Der Text zwischen Markierung und erster
 * Überschrift wird ein eigener Chunk ("Zweck der Regel").</li>
 * <li><b>Unterabschnitt</b>, z. B. "3.3 Zählspiel"</li>
 * <li><b>Teilabschnitt</b>, z. B. "3.3a Gewinner im Zählspiel"</li>
 * </ol>
 *
 * <p>Eine Zeile gilt nur dann als Überschrift, wenn nach der Nummer ein
 * Großbuchstabe folgt und die Nummer größer ist als die vorige. So werden
 * Verweise im Fließtext ("…wie es Regel⏎14.2d verlangt.") nicht für
 * Überschriften gehalten.
 */
public class HeadingChunker implements Chunker {

    /** "3.3 Zählspiel" oder "3.3a Gewinner im Zählspiel": Nummer, optionaler Buchstabe, Großbuchstabe. */
    private static final Pattern HEADING = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})([a-z])?\\s+[\\p{Lu}„].*");

    private final String sectionName;
    private final int maxChars;
    private final String chapterLabel;
    private final Map<Integer, String> chapterTitles;
    private final String chapterStartMarker;
    private final int markerMaxLine;

    /**
     * @param sectionName        Name des Abschnitts, z. B. "Regeln"
     * @param maxChars           gewünschte Höchstlänge eines Chunks
     * @param chapterLabel       Bezeichnung eines Kapitels, z. B. "Regel"
     * @param chapterTitles      Kapitelnummer → Titel, z. B. 3 → "Das Turnier"
     * @param chapterStartMarker Zeile, die den Beginn eines Kapitels markiert, z. B. "Zweck der Regel:"
     * @param markerMaxLine      die Markierung zählt nur in den ersten so vielen Zeilen einer Seite
     */
    public HeadingChunker(String sectionName, int maxChars, String chapterLabel,
            Map<Integer, String> chapterTitles, String chapterStartMarker, int markerMaxLine) {
        this.sectionName = sectionName;
        this.maxChars = maxChars;
        this.chapterLabel = chapterLabel;
        this.chapterTitles = Map.copyOf(chapterTitles);
        this.chapterStartMarker = chapterStartMarker;
        this.markerMaxLine = markerMaxLine;
    }

    @Override
    public List<Document> chunk(List<Document> pages) {
        ChunkAssembler assembler = new ChunkAssembler(sectionName, maxChars);
        String introLabel = chapterStartMarker.replaceAll(":$", "");

        List<Line> buffer = new ArrayList<>();
        int chapter = 0;
        int lastKey = -1;
        boolean introPending = false;
        String subHeading = null;       // z. B. "3.3 Zählspiel"
        String subNumber = null;        // z. B. "3.3"
        String partHeading = null;      // z. B. "3.3a Gewinner im Zählspiel"
        String number = null;           // Nummer des aktuellen Abschnitts

        Document currentPage = null;
        boolean headingOnPage = false;

        for (Line line : Line.fromPages(pages)) {
            if (line.page() != currentPage) {
                currentPage = line.page();
                headingOnPage = false;
            }

            // 1. Beginnt hier ein neues Kapitel?
            if (isChapterStart(line, headingOnPage)) {
                Document markerPage = line.page();
                buffer.removeIf(previous -> previous.page() == markerPage);   // zerstückelten Titel verwerfen
                assembler.add(chain(chapter, subHeading, partHeading), number, buffer);
                buffer = new ArrayList<>();
                introPending = true;
                subHeading = subNumber = partHeading = null;
                continue;
            }

            // 2. Ist die Zeile eine (plausible) Überschrift?
            Matcher heading = HEADING.matcher(line.text());
            if (heading.matches()) {
                int major = Integer.parseInt(heading.group(1));
                int minor = Integer.parseInt(heading.group(2));
                String letter = heading.group(3);
                int key = major * 10_000 + minor * 100 + (letter == null ? 0 : letter.charAt(0) - 'a' + 1);
                boolean plausible = key > lastKey && (chapter == 0 || major == chapter || major == chapter + 1);

                if (plausible) {
                    if (introPending) {
                        assembler.add(chain(major, introLabel, null), String.valueOf(major), buffer);
                        introPending = false;
                    } else {
                        assembler.add(chain(chapter, subHeading, partHeading), number, buffer);
                    }
                    buffer = new ArrayList<>();
                    chapter = major;
                    lastKey = key;
                    headingOnPage = true;

                    String thisSubNumber = major + "." + minor;
                    if (letter == null) {
                        subHeading = line.text();
                        subNumber = thisSubNumber;
                        partHeading = null;
                    } else {
                        if (!thisSubNumber.equals(subNumber)) {
                            subHeading = null;   // Unterabschnitt ohne eigene Überschrift (z. B. 8.1a ohne 8.1)
                            subNumber = thisSubNumber;
                        }
                        partHeading = line.text();
                    }
                    number = thisSubNumber + (letter == null ? "" : letter);
                    continue;
                }
            }

            // 3. Sonst: normale Textzeile
            buffer.add(line);
        }

        if (introPending) {
            assembler.add(chain(chapter + 1, introLabel, null), String.valueOf(chapter + 1), buffer);
        } else {
            assembler.add(chain(chapter, subHeading, partHeading), number, buffer);
        }
        return assembler.build();
    }

    private boolean isChapterStart(Line line, boolean headingOnPage) {
        return line.text().equals(chapterStartMarker)
                && line.indexInPage() < markerMaxLine
                && !headingOnPage;
    }

    /** Baut die Überschriften-Kette, z. B. "Regel 3 – Das Turnier › 3.3 Zählspiel › 3.3a Gewinner". */
    private String chain(int chapter, String subHeading, String partHeading) {
        if (chapter == 0) {
            return sectionName;
        }
        StringBuilder chain = new StringBuilder(chapterLabel).append(' ').append(chapter);
        String title = chapterTitles.get(chapter);
        if (title != null) {
            chain.append(" – ").append(title);
        }
        if (subHeading != null) {
            chain.append(" › ").append(subHeading);
        }
        if (partHeading != null) {
            chain.append(" › ").append(partHeading);
        }
        return chain.toString();
    }
}
