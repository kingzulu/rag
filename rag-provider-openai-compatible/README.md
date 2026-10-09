# rag-provider-openai-compatible

Anbindung an Anbieter mit **OpenAI-kompatibler API** – derselbe Code funktioniert z. B. mit
Scaleway, OpenAI oder Mistral; es ändern sich nur Adresse, Modellname und API-Key.

**Abhängigkeiten:** rag-core, Jackson (JSON)

## Klassen

- **`OpenAiCompatibleClient`** – gemeinsame HTTP-Anbindung: setzt den API-Key, wiederholt bei
  Überlastung (429) oder Serverfehlern (5xx) bis zu 4-mal mit 2, 4 und 8 Sekunden Pause und
  erzeugt Fehlermeldungen **ohne** den API-Key.
- **`OpenAiCompatibleEmbeddingModel`** – setzt `EmbeddingModel` um (`POST /embeddings`),
  verschickt Texte in Paketen zu 32 und ordnet die Vektoren über ihren Index zu.
- **`OpenAiCompatibleChatModel`** – setzt `ChatModel` um (`POST /chat/completions`) mit
  einstellbarer Temperatur und Höchstlänge; abgeschnittene Antworten bekommen einen Hinweis.

Der API-Key kommt aus einer Umgebungsvariable (`fromEnvironment(...)`), nie aus dem Code.

## Tests

Die Tests sprechen nicht mit dem echten Anbieter, sondern mit einem kleinen lokalen Webserver
(`com.sun.net.httpserver`), der die API nachahmt – ohne Internet und ohne Kosten.
