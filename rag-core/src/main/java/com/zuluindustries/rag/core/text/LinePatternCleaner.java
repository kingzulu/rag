package com.zuluindustries.rag.core.text;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.TextCleaner;

/**
 * Entfernt alle Zeilen, die vollständig einem der angegebenen regulären
 * Ausdrücke entsprechen – typisch für Kopf- und Fußzeilen. Leerzeichen am
 * Zeilenanfang und -ende werden beim Vergleich ignoriert.
 */
public class LinePatternCleaner implements TextCleaner {

    private final List<Pattern> patterns;

    public LinePatternCleaner(String... regexes) {
        this.patterns = Arrays.stream(regexes).map(Pattern::compile).toList();
    }

    @Override
    public Optional<Document> clean(Document document) {
        String cleaned = document.text().lines()
                .filter(line -> !matchesAny(line.strip()))
                .collect(Collectors.joining("\n"));
        return TextCleaner.replaceText(document, cleaned);
    }

    private boolean matchesAny(String line) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(line).matches());
    }
}
