package com.zuluindustries.rag.core.text;

import java.util.Optional;
import java.util.stream.Collectors;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.TextCleaner;

/**
 * Vereinheitlicht Leerraum: Tabs und geschützte Leerzeichen werden zu normalen
 * Leerzeichen, mehrere Leerzeichen zu einem, Zeilen werden an beiden Enden
 * gekürzt und mehrere Leerzeilen zu einer zusammengefasst.
 */
public class WhitespaceCleaner implements TextCleaner {

    @Override
    public Optional<Document> clean(Document document) {
        String text = document.text()
                .replace('\t', ' ')
                .replace(' ', ' ')
                .replaceAll(" {2,}", " ");
        text = text.lines()
                .map(String::strip)
                .collect(Collectors.joining("\n"))
                .replaceAll("\n{3,}", "\n\n")
                .strip();
        return TextCleaner.replaceText(document, text);
    }
}
