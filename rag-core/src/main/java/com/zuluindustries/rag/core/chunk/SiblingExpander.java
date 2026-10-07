package com.zuluindustries.rag.core.chunk;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zuluindustries.rag.core.ContextExpander;
import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.SearchResult;

/**
 * Ergänzt jeden Treffer um seine "Geschwister": die übrigen Chunks derselben
 * Unterregel. Wird z. B. "19.2c" gefunden, kommen "19.2a" und "19.2b" dazu;
 * wurde eine lange Teilregel in mehrere Chunks geteilt, kommen deren übrige
 * Teile dazu.
 *
 * <ul>
 * <li>Die Treffer selbst bleiben immer erhalten; ergänzt wird nur bis zu den
 * Obergrenzen für Anzahl und Zeichen.</li>
 * <li>Das Ergebnis ist in Buch-Reihenfolge sortiert (19.2a, 19.2b, 19.2c …).</li>
 * <li>Ergänzte Chunks haben als Ähnlichkeit {@link Double#NaN}, weil sie nicht
 * über die Suche gefunden wurden.</li>
 * </ul>
 */
public class SiblingExpander implements ContextExpander {

    /** "19.2c" → Unterregel "19.2"; Definitionen ("Bunker") und Kapitel ("19") haben keine. */
    private static final Pattern SUBSECTION = Pattern.compile("(\\d{1,2}\\.\\d{1,2})[a-z]?");

    private final List<Document> allChunks;
    private final Map<String, Integer> bookPosition = new HashMap<>();
    private final int maxChunks;
    private final int maxChars;

    /**
     * @param allChunks alle Chunks in Buch-Reihenfolge
     * @param maxChunks höchstens so viele Quellen insgesamt
     * @param maxChars  höchstens so viele Zeichen insgesamt (Treffer werden nie entfernt)
     */
    public SiblingExpander(List<Document> allChunks, int maxChunks, int maxChars) {
        this.allChunks = List.copyOf(allChunks);
        for (int i = 0; i < this.allChunks.size(); i++) {
            bookPosition.put(this.allChunks.get(i).id(), i);
        }
        this.maxChunks = maxChunks;
        this.maxChars = maxChars;
    }

    @Override
    public List<SearchResult> expand(List<SearchResult> hits) {
        Map<String, SearchResult> selected = new LinkedHashMap<>();
        int chars = 0;
        for (SearchResult hit : hits) {
            selected.put(hit.document().id(), hit);
            chars += hit.document().text().length();
        }

        for (SearchResult hit : hits) {
            String subsection = subsectionOf(hit.document());
            if (subsection == null) {
                continue;
            }
            for (Document chunk : allChunks) {
                if (selected.size() >= maxChunks) {
                    return inBookOrder(selected);
                }
                boolean fits = chars + chunk.text().length() <= maxChars;
                if (fits && !selected.containsKey(chunk.id()) && belongsTo(chunk, subsection)) {
                    selected.put(chunk.id(), new SearchResult(chunk, Double.NaN));
                    chars += chunk.text().length();
                }
            }
        }
        return inBookOrder(selected);
    }

    private List<SearchResult> inBookOrder(Map<String, SearchResult> selected) {
        return selected.values().stream()
                .sorted(Comparator.comparingInt(
                        result -> bookPosition.getOrDefault(result.document().id(), Integer.MAX_VALUE)))
                .toList();
    }

    private static String subsectionOf(Document chunk) {
        String number = chunk.metadata().get(ChunkAssembler.NUMBER_KEY);
        if (number == null) {
            return null;
        }
        Matcher matcher = SUBSECTION.matcher(number);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /** "19.2", "19.2a" und "19.2c" gehören zu "19.2", aber nicht "19.20" oder "19.3a". */
    private static boolean belongsTo(Document chunk, String subsection) {
        String number = chunk.metadata().get(ChunkAssembler.NUMBER_KEY);
        if (number == null || !number.startsWith(subsection)) {
            return false;
        }
        return number.length() == subsection.length() || Character.isLetter(number.charAt(subsection.length()));
    }
}
