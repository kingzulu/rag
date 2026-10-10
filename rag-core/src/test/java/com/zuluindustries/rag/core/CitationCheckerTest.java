package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.CitationChecker.Citation;
import com.zuluindustries.rag.core.CitationChecker.Report;

class CitationCheckerTest {

    private static final CitationChecker CHECKER = new CitationChecker(
            Pattern.compile("Regel\\s+(\\d+(?:\\.\\d+[a-z]?)?)(?:\\(\\d+\\))*"));

    private static final List<SearchResult> SOURCES = List.of(
            source("Regel 21 › 21.1a Überblick über Stableford\n\nStableford ist eine Form des Zählspiels."),
            source("Regel 19 › 19.2c Seitliche Erleichterung\n\nInnerhalb von zwei Schlägerlängen."),
            source("Regel 11 › 11.2 Ball absichtlich abgelenkt\n\nSiehe auch Regel 17.1d."));

    private static SearchResult source(String text) {
        return new SearchResult(new Document("chunk-" + text.hashCode(), text), 0.7);
    }

    private static List<String> references(List<Citation> citations) {
        return citations.stream().map(Citation::reference).toList();
    }

    @Test
    void acceptsNumberThatAppearsInCitedSource() {
        Report report = CHECKER.check("Seitlich droppen (Regel 19.2c [2]).", SOURCES);

        assertEquals(List.of(new Citation("Regel 19.2c [2]", "19.2c", List.of(2))), report.supported());
        assertEquals(List.of(), report.unsupported());
    }

    @Test
    void rejectsNumberThatIsNotInCitedSource() {
        Report report = CHECKER.check("Aufnehmen zum Identifizieren (Regel 7.3 [1]).", SOURCES);

        assertEquals(List.of(new Citation("Regel 7.3 [1]", "7.3", List.of(1))), report.unsupported());
    }

    @Test
    void acceptsParentRuleAndCrossReferenceButNotPartOfOtherNumber() {
        Report report = CHECKER.check("Regel 19.2 [2] und Regel 17.1d [3], aber nicht Regel 1.2 [3].", SOURCES);

        assertEquals(List.of("19.2", "17.1d"), references(report.supported()));
        assertEquals(List.of("1.2"), references(report.unsupported()));
    }

    @Test
    void skipsSubparagraphAndAcceptsAnyOfSeveralSources() {
        Report report = CHECKER.check("Laut Regel 19.2c(1) [1][2] gilt das.", SOURCES);

        assertEquals(List.of(new Citation("Regel 19.2c(1) [1][2]", "19.2c", List.of(1, 2))), report.supported());
    }

    @Test
    void rejectsSourceNumberThatDoesNotExist() {
        Report report = CHECKER.check("Regel 19.2c [7]", SOURCES);

        assertEquals(List.of("19.2c"), references(report.unsupported()));
    }

    @Test
    void checksReferenceWithoutSourceAgainstAllSources() {
        Report report = CHECKER.check("Nach Regel 17.1d gilt das, nach Regel 7.3 nicht.", SOURCES);

        assertEquals(List.of("17.1d"), references(report.supported()));   // steht in Quelle 3
        assertEquals(List.of("7.3"), references(report.unsupported()));    // steht in keiner Quelle
    }

    @Test
    void describesProblemsForTheModel() {
        List<String> problems = CHECKER.problems("Regel 7.3 [1] und Regel 4.2", SOURCES);

        assertEquals(List.of("„Regel 7.3 [1]“ steht nicht in der angegebenen Quelle",
                "„Regel 4.2“ steht in keiner der Quellen"), problems);
    }
}
