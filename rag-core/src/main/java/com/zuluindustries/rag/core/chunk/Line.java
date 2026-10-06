package com.zuluindustries.rag.core.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.zuluindustries.rag.core.Document;

/**
 * Eine Textzeile, die weiß, von welcher Seite sie stammt.
 *
 * @param text        der Zeilentext
 * @param page        die Seite, auf der die Zeile steht
 * @param indexInPage die Position der Zeile auf ihrer Seite (ab 0)
 */
public record Line(String text, Document page, int indexInPage) {

    private static final Pattern HYPHEN_AT_END = Pattern.compile(".*\\p{L}-");
    private static final Pattern STARTS_LOWERCASE = Pattern.compile("\\p{Ll}.*");

    /**
     * Fügt die Zeilen aller Seiten zu einer Liste zusammen. Ist ein Wort über
     * die Seitengrenze getrennt ("zu-" am Seitenende, "rück" auf der nächsten
     * Seite), werden die beiden Zeilen zu einer zusammengefügt.
     */
    public static List<Line> fromPages(List<Document> pages) {
        List<Line> lines = new ArrayList<>();
        for (Document page : pages) {
            List<String> pageLines = page.text().lines().toList();
            for (int i = 0; i < pageLines.size(); i++) {
                String text = pageLines.get(i);
                if (i == 0 && continuesHyphenatedWord(lines, text)) {
                    Line previous = lines.removeLast();
                    String joined = previous.text().substring(0, previous.text().length() - 1) + text;
                    lines.add(new Line(joined, previous.page(), previous.indexInPage()));
                } else {
                    lines.add(new Line(text, page, i));
                }
            }
        }
        return lines;
    }

    private static boolean continuesHyphenatedWord(List<Line> lines, String firstLineOfPage) {
        return !lines.isEmpty()
                && HYPHEN_AT_END.matcher(lines.getLast().text()).matches()
                && STARTS_LOWERCASE.matcher(firstLineOfPage).matches();
    }
}
