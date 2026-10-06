package com.zuluindustries.rag.core.text;

import java.util.Optional;
import java.util.regex.Pattern;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.TextCleaner;

/**
 * Fügt Wörter zusammen, die am Zeilenende mit Bindestrich getrennt wurden.
 * Drei Fälle werden unterschieden:
 * <ul>
 * <li>"Ein-⏎und Ausgang" wird zu "Ein- und Ausgang" (Ergänzungsstrich vor und/oder)</li>
 * <li>"zurückzu-⏎legen" wird zu "zurückzulegen" (Silbentrennung, nächste Zeile beginnt klein)</li>
 * <li>"Netto-⏎Ergebnisse" wird zu "Netto-Ergebnisse" (echter Bindestrich, nächste Zeile beginnt groß)</li>
 * </ul>
 */
public class HyphenationCleaner implements TextCleaner {

    // \p{L} = beliebiger Buchstabe, \p{Ll} = Kleinbuchstabe (jeweils inkl. Umlaute und ß)
    private static final String BREAK = "-[ \\t]*\\n[ \\t]*";

    private static final Pattern CONJUNCTION_BREAK = Pattern.compile("(\\p{L})" + BREAK + "((?:und|oder)\\b)");
    private static final Pattern SYLLABLE_BREAK = Pattern.compile("(\\p{L})" + BREAK + "(\\p{Ll})");
    private static final Pattern WORD_BREAK = Pattern.compile("(\\p{L})" + BREAK + "(\\p{L})");

    @Override
    public Optional<Document> clean(Document document) {
        // Reihenfolge wichtig: der speziellste Fall zuerst.
        String text = CONJUNCTION_BREAK.matcher(document.text()).replaceAll("$1- $2");
        text = SYLLABLE_BREAK.matcher(text).replaceAll("$1$2");
        text = WORD_BREAK.matcher(text).replaceAll("$1-$2");
        return TextCleaner.replaceText(document, text);
    }
}
