# rag-app

Setzt die Module für die **Golfregeln** zusammen und enthält die startbaren Programme und Auswertungen.

**Abhängigkeiten:** rag-core, rag-source-pdf, rag-provider-openai-compatible, rag-store-memory

## Konfiguration

- **`GolfRules`** – alles Golf-Spezifische: Seitenbereiche, Kopfzeilen-Muster, Regeltitel,
  Abschnitte mit ihrer Zerlege-Strategie, die Prompts (Antwort, HyDE, Nachbesserung), Muster für
  Regelverweise und veraltete Begriffe, Einstellungen für Suche und Kontext; dazu der Ablauf
  "PDF lesen → bereinigen → zerlegen". Die Kommentare halten fest, welche Fassungen getestet und
  warum sie verworfen wurden.
- **`GolfAssistant`** – baut den Assistenten zusammen (HyDE, Suche mit Zusammenführung, Schwelle,
  Geschwister-Abschnitte, Prüfung) – an einer Stelle, damit `AskApp` und `AnswerEvaluation` garantiert
  gleich arbeiten.
- **`Scaleway`** – Adresse, Modelle, Name der Key-Variable, Anfrage-Anweisung und Schwelle.
- **`ChatProviders`** – Antwortmodelle von Scaleway und OpenAI; Standard für `AskApp` ist OpenAI
  (Vergleich vom 10.10.2026 im Kommentar).
- **`SearchIndex`** – lädt den Suchindex `data/golfregeln/suchindex.jsonl`.

## Programme

| Programm | Wofür | Keys |
|---|---|---|
| `RagApp` | PDF verarbeiten, Zwischenergebnisse als Textdateien in `data/golfregeln/` | – |
| `IndexApp` | Suchindex aufbauen/aktualisieren | Scaleway |
| `SearchApp` | interaktive Suche (nur Treffer) | Scaleway |
| `AskApp` | **Fragen stellen, Antwort mit Quellen**; erstes Argument `--openai` (Standard) oder `--scaleway` wählt das Antwortmodell | Scaleway + OpenAI |
| `EmbeddingDemo` | erstes Experiment mit Embeddings | Scaleway |
| `SearchEvaluation` | Suchqualität messen: Treffer@1/@5, Trennung von themenfremden Fragen, falsch abgewiesene Fragen; Originalfrage, HyDE und Zusammenführung im Vergleich | Scaleway |
| `AnswerEvaluation` | 14 Testfragen beantworten, Nachbesserungen und unbelegte Angaben zählen; Argument `scaleway` (Standard) oder `openai` | Scaleway (+ OpenAI) |

Die Keys stehen in Umgebungsvariablen: `SCW_SECRET_KEY` und `OPENAI_API_KEY`
(Eclipse: *Run Configurations → Environment*). Die Programme starten im Projektordner
`rag-app`; Pfade zu `data/` sind deshalb relativ mit `../`.

## Messen statt raten

Jede Änderung an Prompt oder Suche wird mit `SearchEvaluation` bzw. `AnswerEvaluation` gemessen –
eine Änderung nach der anderen, wegen der Schwankung zwischen Läufen meist zweimal. Was nicht
messbar besser ist, wird wieder entfernt.
