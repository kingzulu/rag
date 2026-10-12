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
| `ChatModel` | Sprachmodell | `OpenAiCompatibleChatModel` (rag-provider-openai-compatible) |
| `QueryRewriter` | übersetzt die Frage für die Suche; leer = themenfremd | `ChatQueryRewriter` (z. B. HyDE), `TermQueryRewriter` |
| `Searcher` | sucht zu Frage und Suchtext | `Searcher.of(retriever)`, `FusionSearcher` |
| `ContextExpander` | ergänzt Suchtreffer | `SiblingExpander` |
| `AnswerCheck` | prüft eine Antwort gegen ihre Quellen | `CitationChecker`, `ForbiddenTermCheck` |

## Der Ablauf

- **Indexieren:** `Indexer` bettet Dokumente ein und füllt den `VectorStore` – nur neue oder geänderte.
- **Suchen:** `Retriever` bettet einen Text ein (optional mit Anweisung für Suchanfragen) und sucht
  im `VectorStore`. `FusionSearcher` sucht zweimal – mit der Frage und mit dem Suchtext – und
  führt beide Listen mit `RankFusion` (Reciprocal Rank Fusion) zusammen.
- **Antworten:** `AnswerGenerator`
  1. Frage übersetzen (`QueryRewriter`) – themenfremd? Dann Schluss, ohne Suche.
  2. Suchen (`Searcher`) und Schwelle prüfen (höchster Ähnlichkeitswert der Treffer).
  3. Treffer ergänzen (`ContextExpander`).
  4. Die **Originalfrage** mit nummerierten Quellen an das Sprachmodell.
  5. Antwort prüfen (`AnswerCheck`); bei Problemen einmal nachbessern lassen, verbleibende
     Probleme stehen in `Answer.problems()`.

  Einstellungen (Systemanweisung, Treffer, Schwelle, Prüfung …) bündelt `AnswerGenerator.Settings`.

## Bausteine im Einzelnen

- **`ChatQueryRewriter`** – ein Sprachmodell schreibt die Frage um; was genau, bestimmt der Prompt
  (z. B. ein Absatz im Stil des Dokuments – "HyDE"). Antwortet es "keine", ist die Frage themenfremd.
- **`RankFusion`** – jedes Dokument bekommt pro Liste `1 / (k + Platz)` Punkte; gezählt werden
  Plätze, nicht Ähnlichkeitswerte, weil diese zwischen verschiedenen Suchen nicht vergleichbar sind.
- **`CitationChecker`** – steht ein Verweis wie "Regel 19.2c [4]" wirklich in Quelle 4? Wie ein
  Verweis aussieht, gibt ein Muster vor. Erkennt falsche Verweise, keine falschen Inhalte.
- **`ForbiddenTermCheck`** – meldet Begriffe, die nicht vorkommen dürfen (z. B. aus veralteten Fassungen).

## Pakete

- **`text`** – Bereiniger: Seitenzahlen, Kopfzeilen (Muster), Silbentrennung, Leerraum;
  `CompositeCleaner` schaltet sie hintereinander.
- **`chunk`** – Zerlege-Strategien: nach Größe (`SizeChunker`), nach nummerierten Überschriften
  (`HeadingChunker`), nach Begriffen (`TermChunker`); `ChunkAssembler` baut daraus Chunks mit
  Überschriften-Kette und Metadaten. Dazu `SiblingExpander` (Geschwister-Abschnitte ergänzen).
