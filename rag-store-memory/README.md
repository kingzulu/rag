# rag-store-memory

Vektorspeicher im Arbeitsspeicher, gespeichert als Datei.

**Abhängigkeiten:** rag-core, Jackson (JSON)

## InMemoryVectorStore

Setzt `VectorStore` um:

- **Suche:** vergleicht die Anfrage mit **allen** gespeicherten Vektoren (Kosinus-Ähnlichkeit,
  "Brute Force") – bei einigen tausend Einträgen nur Millisekunden; optional mit Filter
  (z. B. nur Definitionen)
- **Speichern/Laden** als JSON-Lines-Datei: eine Kopfzeile mit Modellname und Vektorlänge, danach
  eine Zeile pro Dokument mit Text, Metadaten und Vektor. Die Kopfzeile verhindert, dass Vektoren
  verschiedener Embedding-Modelle vermischt werden.
- `vectorFor(...)` erkennt unveränderte Dokumente (gleiche id **und** gleicher Text) – so muss der
  `Indexer` nur Neues oder Geändertes einbetten.

Für sehr große Datenmengen wäre später eine Vektordatenbank (z. B. pgvector) die bessere Wahl –
als weiteres Modul, das `VectorStore` umsetzt.
