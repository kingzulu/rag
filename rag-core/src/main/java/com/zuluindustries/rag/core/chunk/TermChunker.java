package com.zuluindustries.rag.core.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.zuluindustries.rag.core.Chunker;
import com.zuluindustries.rag.core.Document;

/**
 * Zerlegt Glossare und Definitionslisten: ein Chunk pro Begriff.
 *
 * <p>Eine Zeile gilt als Begriff, wenn sie
 * <ul>
 * <li>kurz ist (höchstens {@code maxTermLength} Zeichen, {@code maxTermWords} Wörter),</li>
 * <li>mit einem Großbuchstaben beginnt und nicht mit einem Satzzeichen endet,</li>
 * <li>die Zeile davor einen Satz abschließt (oder es die erste Zeile ist) und</li>
 * <li>die Zeile danach mit einem Großbuchstaben beginnt.</li>
 * </ul>
 */
public class TermChunker implements Chunker {

    private static final Pattern STARTS_UPPERCASE = Pattern.compile("[\\p{Lu}„].*");
    private static final Pattern ENDS_WITH_PUNCTUATION = Pattern.compile(".*[.,:;]");
    private static final Pattern ENDS_SENTENCE = Pattern.compile(".*[.)“”!?]");

    private final String sectionName;
    private final int maxChars;
    private final int maxTermLength;
    private final int maxTermWords;

    public TermChunker(String sectionName, int maxChars, int maxTermLength, int maxTermWords) {
        this.sectionName = sectionName;
        this.maxChars = maxChars;
        this.maxTermLength = maxTermLength;
        this.maxTermWords = maxTermWords;
    }

    @Override
    public List<Document> chunk(List<Document> pages) {
        ChunkAssembler assembler = new ChunkAssembler(sectionName, maxChars);
        List<Line> lines = Line.fromPages(pages);

        String term = null;
        List<Line> buffer = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (isTerm(lines, i)) {
                assembler.add(heading(term), term, buffer);
                term = lines.get(i).text();
                buffer = new ArrayList<>();
            } else {
                buffer.add(lines.get(i));
            }
        }
        assembler.add(heading(term), term, buffer);
        return assembler.build();
    }

    private String heading(String term) {
        return term == null ? sectionName : sectionName + " › " + term;
    }

    private boolean isTerm(List<Line> lines, int i) {
        String text = lines.get(i).text();
        boolean shortEnough = text.length() <= maxTermLength && text.split(" ").length <= maxTermWords;
        boolean looksLikeTerm = STARTS_UPPERCASE.matcher(text).matches()
                && !ENDS_WITH_PUNCTUATION.matcher(text).matches();
        boolean afterSentence = i == 0 || ENDS_SENTENCE.matcher(lines.get(i - 1).text()).matches();
        boolean beforeSentence = i + 1 < lines.size()
                && STARTS_UPPERCASE.matcher(lines.get(i + 1).text()).matches();
        return shortEnough && looksLikeTerm && afterSentence && beforeSentence;
    }
}
