package com.zuluindustries.rag.source.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zuluindustries.rag.core.Document;

class PdfDocumentSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsOneDocumentPerPageAndSkipsEmptyPages() throws IOException {
        Path pdf = tempDir.resolve("testregeln.pdf");
        writePdf(pdf, "Regel 1 Das Spiel", null, "Regel 2 Der Platz");

        List<Document> documents = new PdfDocumentSource(pdf).load();

        assertEquals(2, documents.size());

        Document first = documents.get(0);
        assertEquals("testregeln#seite-1", first.id());
        assertTrue(first.text().contains("Regel 1 Das Spiel"));
        assertEquals("testregeln.pdf", first.metadata().get("quelle"));
        assertEquals("1", first.metadata().get("seite"));

        // Seite 2 ist leer – das zweite Dokument muss also von Seite 3 stammen.
        Document second = documents.get(1);
        assertEquals("testregeln#seite-3", second.id());
        assertTrue(second.text().contains("Regel 2 Der Platz"));
    }

    @Test
    void readsOnlyRequestedPageRange() throws IOException {
        Path pdf = tempDir.resolve("testregeln.pdf");
        writePdf(pdf, "Vorwort", "Regel 1 Das Spiel", "Regel 2 Der Platz", "Index");

        List<Document> documents = new PdfDocumentSource(pdf, 2, 3, true).load();

        assertEquals(List.of("testregeln#seite-2", "testregeln#seite-3"),
                documents.stream().map(Document::id).toList());
    }

    @Test
    void rejectsInvalidPageRange() throws IOException {
        Path pdf = tempDir.resolve("testregeln.pdf");
        writePdf(pdf, "Regel 1");

        assertThrows(IllegalArgumentException.class, () -> new PdfDocumentSource(pdf, 5, 3, false));
    }

    @Test
    void rejectsMissingFile() {
        Path missing = tempDir.resolve("gibt-es-nicht.pdf");

        assertThrows(IllegalArgumentException.class, () -> new PdfDocumentSource(missing));
    }

    /** Erzeugt ein PDF mit einer Seite pro Text; {@code null} ergibt eine leere Seite. */
    private static void writePdf(Path file, String... pageTexts) throws IOException {
        try (PDDocument pdf = new PDDocument()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                if (text == null) {
                    continue;
                }
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            pdf.save(file.toFile());
        }
    }
}
