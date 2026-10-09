# RAG – Lernprojekt

Eine modulare Java-Implementierung von **Retrieval-Augmented Generation (RAG)** zu Lernzwecken.

Ziel: Dieselbe Implementierung soll für ganz unterschiedliche Wissensquellen nutzbar sein –
ein neuer Kontext braucht nur eine neue Konfiguration, keinen neuen Kern-Code.

## Erster Anwendungsfall

Fragen zu den Offiziellen Golfregeln (PDF) beantworten – mit Quellenangaben, nur auf Grundlage des
Regelbuchs. Später: weitere Quellen, z. B. die SAP-BTP-Dokumentation.

## Module

| Modul | Inhalt |
|---|---|
| [rag-core](rag-core/README.md) | Interfaces und allgemeine Bausteine – ohne externe Bibliotheken |
| [rag-source-pdf](rag-source-pdf/README.md) | PDF-Dateien seitenweise lesen (Apache PDFBox) |
| [rag-provider-openai-compatible](rag-provider-openai-compatible/README.md) | Embeddings und Chat über OpenAI-kompatible APIs (z. B. Scaleway) |
| [rag-store-memory](rag-store-memory/README.md) | Vektorspeicher im Arbeitsspeicher, gespeichert als JSON-Lines-Datei |
| [rag-app](rag-app/README.md) | Golfregel-Konfiguration und startbare Programme |

Das Eltern-Projekt (`pom.xml` in diesem Ordner) legt Java-Version und Bibliotheksversionen zentral
fest und baut alle Module gemeinsam.

## Technik

- Java 21, Maven (Multi-Modul), entwickelt in Eclipse
- Embeddings (`bge-multilingual-gemma2`) und Antworten (`mistral-small-3.2`) über die Scaleway
  Generative APIs (OpenAI-kompatibel)
- Vektorspeicher: eigener In-Memory-Store mit Datei-Persistenz

## Lokale Daten

Quelldokumente und erzeugte Vektordateien liegen im Ordner `data/` und werden **nicht** versioniert.

## API-Key

Der Scaleway Secret Key wird als Umgebungsvariable `SCW_SECRET_KEY` gesetzt
(in Eclipse: *Run Configurations → Environment*) – niemals im Code oder im Repository.
