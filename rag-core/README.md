# rag-core

Der Kern des RAG-Systems: **Interfaces** für alle austauschbaren Bausteine und **allgemeine
Umsetzungen**, die ohne externe Bibliotheken auskommen. Nichts hier weiß etwas von Golf, PDFs,
Scaleway oder Dateiformaten.

**Abhängigkeiten:** keine (nur JUnit für Tests)

## Interfaces

| Interface | Aufgabe | Umsetzungen |
|---|---|---|
| `DocumentSource` | liefert Dokumente | `PdfDocumentSource` (rag-source-pdf) |
| `TextCleaner` | bereinigt Text | Paket `text` |
| `Chunker` | zerlegt Seiten in Abschnitte | Paket `chunk` |
| `EmbeddingModel` | Text → Vektor | `OpenAiCompatibleEmbeddingModel` (rag-provider-openai-compatible) |
| `VectorStore` | speichert Vektoren, sucht ähnliche | `InMemoryVectorStore` (rag-store-memory) |
| `ChatModel` | Sprachmodell für Antworten | `OpenAiCompatibleChatModel` (rag-provider-openai-compatible) |
| `QueryRewriter` | ergänzt die Frage für die Suche | `TermQueryRewriter` |
| `ContextExpander` | ergänzt Suchtreffer | `SiblingExpander` |

## Der Ablauf

- **Indexieren:** `Indexer` bettet Dokumente ein und füllt den `VectorStore` – nur neue oder geänderte.
- **Suchen:** `Retriever` bettet die Frage ein und sucht im `VectorStore`.
- **Antworten:** `AnswerGenerator` – Schwelle prüfen → Frage ergänzen → suchen → Treffer ergänzen
  → Sprachmodell mit nummerierten Quellen fragen.

## Pakete

- **`text`** – Bereiniger: Seitenzahlen, Kopfzeilen (Muster), Silbentrennung, Leerraum;
  `CompositeCleaner` schaltet sie hintereinander.
- **`chunk`** – Zerlege-Strategien: nach Größe (`SizeChunker`), nach nummerierten Überschriften
  (`HeadingChunker`), nach Begriffen (`TermChunker`); `ChunkAssembler` baut daraus Chunks mit
  Überschriften-Kette und Metadaten. Dazu `SiblingExpander` (Geschwister-Abschnitte ergänzen).
