package com.zuluindustries.rag.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Prüft die Belege einer Antwort: Steht ein Verweis wie "Regel 19.2c [4]"
 * wirklich in der angegebenen Quelle? So lassen sich erfundene Belege ohne
 * Sprachmodell erkennen – kostenlos und wiederholbar.
 *
 * <p>Wie ein Verweis aussieht, gibt das Muster vor; Gruppe 1 ist die Nummer,
 * z. B. "19.2c". Direkt dahinter erwartet der Prüfer die Quellenangaben, z. B.
 * "[4]" oder "[2][3]". Belegt ist ein Verweis, wenn seine Nummer in mindestens
 * einer der angegebenen Quellen vorkommt – als ganze Nummer ("1.2" steckt nicht
 * in "11.2"), Unterabschnitte zählen mit ("19.2" ist mit "19.2c" belegt). Ein
 * Verweis <b>ohne</b> Quellenangabe muss in irgendeiner der Quellen vorkommen –
 * sonst stammt er vermutlich aus dem Gedächtnis des Modells.
 *
 * <p>Grenze: Erkannt werden nur falsche Verweise, keine falschen Inhalte mit
 * richtigem Verweis.
 */
public class CitationChecker implements AnswerCheck {

    /**
     * Ein Verweis in der Antwort.
     *
     * @param quote     die Stelle in der Antwort, z. B. "Regel 17.1d(1) [5][6]"
     * @param reference die Nummer, z. B. "17.1d"
     * @param sources   die angegebenen Quellen (ab 1); leer, wenn keine angegeben ist
     */
    public record Citation(String quote, String reference, List<Integer> sources) {

        public Citation {
            sources = List.copyOf(sources);
        }

        @Override
        public String toString() {
            return quote;
        }
    }

    /**
     * @param supported   Nummer steht in einer der angegebenen Quellen (ohne Angabe: in irgendeiner)
     * @param unsupported Nummer steht dort nicht (oder die angegebene Quelle gibt es nicht)
     */
    public record Report(List<Citation> supported, List<Citation> unsupported) {

        /** Anzahl der geprüften Verweise. */
        public int checked() {
            return supported.size() + unsupported.size();
        }
    }

    private static final Pattern SOURCE_LIST = Pattern.compile("\\s*((?:\\[\\d+\\])+)");
    private static final Pattern SOURCE_NUMBER = Pattern.compile("\\[(\\d+)\\]");

    private final Pattern reference;

    /** @param reference Muster für einen Verweis; Gruppe 1 ist die Nummer */
    public CitationChecker(Pattern reference) {
        this.reference = reference;
    }

    /** @param sources die Quellen in der Reihenfolge, in der sie dem Modell nummeriert gezeigt wurden */
    public Report check(String answer, List<SearchResult> sources) {
        List<Citation> supported = new ArrayList<>();
        List<Citation> unsupported = new ArrayList<>();

        Matcher matcher = reference.matcher(answer);
        while (matcher.find()) {
            Matcher list = SOURCE_LIST.matcher(answer).region(matcher.end(), answer.length());
            boolean hasSources = list.lookingAt();
            int end = hasSources ? list.end() : matcher.end();
            Citation citation = new Citation(answer.substring(matcher.start(), end).strip(), matcher.group(1),
                    hasSources ? sourceNumbers(list.group(1)) : List.of());
            if (isSupported(citation, sources)) {
                supported.add(citation);
            } else {
                unsupported.add(citation);
            }
        }
        return new Report(List.copyOf(supported), List.copyOf(unsupported));
    }

    @Override
    public List<String> problems(String answer, List<SearchResult> sources) {
        return check(answer, sources).unsupported().stream()
                .map(citation -> citation.sources().isEmpty()
                        ? "„" + citation + "“ steht in keiner der Quellen"
                        : "„" + citation + "“ steht nicht in der angegebenen Quelle")
                .toList();
    }

    /** "[2][3]" → 2, 3 */
    private static List<Integer> sourceNumbers(String list) {
        List<Integer> numbers = new ArrayList<>();
        Matcher number = SOURCE_NUMBER.matcher(list);
        while (number.find()) {
            numbers.add(Integer.parseInt(number.group(1)));
        }
        return numbers;
    }

    private static boolean isSupported(Citation citation, List<SearchResult> sources) {
        // Ganze Nummer: davor keine Ziffer und kein Punkt, dahinter keine Ziffer (ein Buchstabe ist erlaubt).
        Pattern wholeNumber = Pattern.compile("(?<![\\d.])" + Pattern.quote(citation.reference()) + "(?!\\d)");
        List<Integer> candidates = citation.sources().isEmpty()
                ? IntStream.rangeClosed(1, sources.size()).boxed().toList()
                : citation.sources();
        return candidates.stream()
                .filter(n -> n >= 1 && n <= sources.size())
                .anyMatch(n -> wholeNumber.matcher(sources.get(n - 1).document().text()).find());
    }
}
