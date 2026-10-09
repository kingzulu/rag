# rag-app

Setzt die Module für die **Golfregeln** zusammen und enthält die startbaren Programme.

**Abhängigkeiten:** rag-core, rag-source-pdf, rag-provider-openai-compatible, rag-store-memory

## Konfiguration

- **`GolfRules`** – alles Golf-Spezifische: Seitenbereiche, Kopfzeilen-Muster, Regeltitel,
  Abschnitte mit ihrer Zerlege-Strategie, Systemanweisung und Einstellungen für die Antworten;
  dazu der Ablauf "PDF lesen → bereinigen → zerlegen".
- **`Scaleway`** – Adresse, Modelle, Name der Key-Variable, Anfrage-Anweisung und Schwelle.
- **`SearchIndex`** – lädt den Suchindex `data/golfregeln/suchindex.jsonl`.

## Programme

| Programm | Wofür | Braucht Key |
|---|---|---|
| `RagApp` | PDF verarbeiten, Zwischenergebnisse als Textdateien in `data/golfregeln/` | nein |
| `IndexApp` | Suchindex aufbauen/aktualisieren | ja |
| `SearchApp` | interaktive Suche (nur Treffer) | ja |
| `AskApp` | **Fragen stellen, Antwort mit Quellen** | ja |
| `EmbeddingDemo` | erstes Experiment mit Embeddings (Schritt 5) | ja |
| `SearchEvaluation` | Suchqualität messen (Treffer@1/@5, Schwelle, Begriffe) | ja |
| `AnswerEvaluation` | Antworten mehrerer Varianten vergleichen | ja |

Der Key ist der Scaleway Secret Key in der Umgebungsvariable `SCW_SECRET_KEY`
(Eclipse: *Run Configurations → Environment*). Die Programme starten im Projektordner
`rag-app`; Pfade zu `data/` sind deshalb relativ mit `../`.
