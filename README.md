# RAG – Lernprojekt

Eine modulare Java-Implementierung von **Retrieval-Augmented Generation (RAG)** zu Lernzwecken.

Ziel: Dieselbe Implementierung soll für ganz unterschiedliche Wissensquellen nutzbar sein –
ein neuer Kontext braucht nur eine neue Konfiguration, keinen neuen Kern-Code.

## Erster Anwendungsfall

Fragen zu den Offiziellen Golfregeln (PDF) beantworten – mit Quellenangaben, nur auf Grundlage des
Regelbuchs. Später: weitere Quellen, z. B. die SAP-BTP-Dokumentation.

## So wird eine Frage beantwortet

1. **Übersetzen (HyDE):** Ein Sprachmodell schreibt einen kurzen Absatz im Stil des Regelbuchs,
   der die Frage beantworten könnte. Er dient nur der Suche und darf falsch sein. Hat die Frage
   nichts mit Golf zu tun, ist hier Schluss.
2. **Suchen:** mit dem Absatz und mit der Originalfrage; beide Trefferlisten werden mit
   *Reciprocal Rank Fusion* zusammengeführt. Liegt der beste Treffer unter einer Schwelle, gibt es
   keine Antwort.
3. **Kontext:** Zu jedem Treffer kommen die übrigen Abschnitte derselben Unterregel dazu.
4. **Antworten:** Ein Sprachmodell beantwortet die **Originalfrage** nur aus den nummerierten
   Quellen.
5. **Prüfen:** Jede Regelnummer muss in einer Quelle stehen, kein Begriff aus den Regeln vor 2019.
   Sonst wird einmal nachgebessert; bleibt etwas, kommt die Antwort mit Warnhinweis.

## Module

| Modul | Inhalt |
|---|---|
| [rag-core](rag-core/README.md) | Interfaces und allgemeine Bausteine – ohne externe Bibliotheken |
| [rag-source-pdf](rag-source-pdf/README.md) | PDF-Dateien seitenweise lesen (Apache PDFBox) |
| [rag-provider-openai-compatible](rag-provider-openai-compatible/README.md) | Embeddings und Chat über OpenAI-kompatible APIs (Scaleway, OpenAI) |
| [rag-store-memory](rag-store-memory/README.md) | Vektorspeicher im Arbeitsspeicher, gespeichert als JSON-Lines-Datei |
| [rag-app](rag-app/README.md) | Golfregel-Konfiguration, Auswertungen und startbare Programme |

Das Eltern-Projekt (`pom.xml` in diesem Ordner) legt Java-Version und Bibliotheksversionen zentral
fest und baut alle Module gemeinsam.

## Technik

- Java 21, Maven (Multi-Modul), entwickelt in Eclipse
- Embeddings (`bge-multilingual-gemma2`) und HyDE-Absatz (`mistral-small-3.2`) über die Scaleway
  Generative APIs (OpenAI-kompatibel)
- Antworten standardmäßig über OpenAI (`gpt-6.1-sol`); Mistral Small ist wählbar, verweigert aber
  Fragen, deren Antwort aus den Quellen gefolgert werden muss
- Vektorspeicher: eigener In-Memory-Store mit Datei-Persistenz

## Lokale Daten

Quelldokumente und erzeugte Vektordateien liegen im Ordner `data/` und werden **nicht** versioniert.

## API-Keys

Als Umgebungsvariablen setzen (in Eclipse: *Run Configurations → Environment*) – niemals im Code
oder im Repository:

- `SCW_SECRET_KEY` – Scaleway (Embeddings, HyDE; Antworten mit `--scaleway`)
- `OPENAI_API_KEY` – OpenAI (Antworten)
