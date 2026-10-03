# RAG – Lernprojekt

Eine modulare Java-Implementierung von **Retrieval-Augmented Generation (RAG)** zu Lernzwecken.

Ziel: Dieselbe Implementierung soll für ganz unterschiedliche Wissensquellen nutzbar sein –
ein neuer Kontext braucht nur eine neue Konfiguration, keinen neuen Kern-Code.

## Erster Anwendungsfall

Embeddings für die internationalen Golfregeln (PDF), danach semantische Suche darin.
Später: weitere Quellen, z. B. die SAP-BTP-Dokumentation.

## Technik

- Java 21, Maven (Multi-Modul), entwickelt in Eclipse
- Embeddings über die Scaleway Generative APIs (OpenAI-kompatibel)
- Vektorspeicher: zunächst eigener In-Memory-Store mit Datei-Persistenz

## Lokale Daten

Quelldokumente und erzeugte Vektordateien liegen im Ordner `data/` und werden **nicht** versioniert.

## API-Key

Der Scaleway Secret Key wird als Umgebungsvariable `SCW_SECRET_KEY` gesetzt
(in Eclipse: *Run Configurations → Environment*) – niemals im Code oder im Repository.
