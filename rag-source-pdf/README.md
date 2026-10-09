# rag-source-pdf

Liest PDF-Dateien als Quelle für das RAG-System.

**Abhängigkeiten:** rag-core, Apache PDFBox

## PdfDocumentSource

Setzt `DocumentSource` um und liefert **ein Dokument pro Seite**:

- `id`: Dateiname + Seite, z. B. `offizielle_golfregeln_2023#seite-41`
- Metadaten: `quelle` (Dateiname) und `seite` (Seite in der PDF-Datei)
- optional nur ein **Seitenbereich** (z. B. 20–268)
- optional **`sortByPosition`**: Text nach seiner Position auf der Seite ordnen statt in der
  Reihenfolge, in der er in der Datei gespeichert ist – repariert Kästen und Titel, die sonst am
  Seitenende landen, kann aber nebeneinanderstehenden Text vermischen
- Seiten ohne Text (Leer- und Bildseiten) werden übersprungen

Der Text wird **roh** übernommen; das Aufräumen übernehmen die Bereiniger aus rag-core.
