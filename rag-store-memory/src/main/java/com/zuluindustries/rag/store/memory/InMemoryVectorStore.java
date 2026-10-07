package com.zuluindustries.rag.store.memory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.SearchResult;
import com.zuluindustries.rag.core.VectorMath;
import com.zuluindustries.rag.core.VectorStore;

/**
 * Vektorspeicher im Arbeitsspeicher. Die Suche vergleicht die Anfrage mit
 * <b>allen</b> gespeicherten Vektoren ("Brute Force") – bei einigen tausend
 * Einträgen dauert das nur Millisekunden.
 *
 * <p>Gespeichert wird als JSON-Lines-Datei (eine JSON-Zeile pro Eintrag), damit
 * man hineinschauen kann:
 * <pre>
 * {"model":"bge-multilingual-gemma2","dimensions":3584,"count":487}
 * {"id":"…","text":"…","metadata":{…},"vector":[0.012,-0.087,…]}
 * …
 * </pre>
 * Die erste Zeile nennt das Embedding-Modell: Vektoren verschiedener Modelle
 * sind nicht miteinander vergleichbar.
 */
public class InMemoryVectorStore implements VectorStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    private record Entry(Document document, float[] vector) {
    }

    private final String model;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** @param model Name des Embedding-Modells, mit dem die Vektoren erzeugt werden */
    public InMemoryVectorStore(String model) {
        this.model = model;
    }

    public String model() {
        return model;
    }

    @Override
    public void add(Document document, float[] vector) {
        int dimensions = dimensions();
        if (dimensions > 0 && vector.length != dimensions) {
            throw new IllegalArgumentException("Vektor hat " + vector.length + " statt " + dimensions + " Werte");
        }
        entries.put(document.id(), new Entry(document, vector));
    }

    @Override
    public Optional<float[]> vectorFor(Document document) {
        Entry entry = entries.get(document.id());
        return entry != null && entry.document().text().equals(document.text())
                ? Optional.of(entry.vector())
                : Optional.empty();
    }

    @Override
    public int retainAll(Collection<String> ids) {
        int before = entries.size();
        entries.keySet().retainAll(ids);
        return before - entries.size();
    }

    @Override
    public List<SearchResult> search(float[] query, int topK) {
        return entries.values().stream()
                .map(entry -> new SearchResult(entry.document(), VectorMath.cosineSimilarity(query, entry.vector())))
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public int size() {
        return entries.size();
    }

    /** Länge der gespeicherten Vektoren, 0 wenn der Speicher leer ist. */
    public int dimensions() {
        return entries.isEmpty() ? 0 : entries.values().iterator().next().vector().length;
    }

    // ---- Speichern und Laden ----

    public void save(Path file) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file)) {
            ObjectNode header = JSON.createObjectNode()
                    .put("model", model)
                    .put("dimensions", dimensions())
                    .put("count", entries.size());
            out.write(JSON.writeValueAsString(header));
            out.newLine();

            for (Entry entry : entries.values()) {
                ObjectNode line = JSON.createObjectNode();
                line.put("id", entry.document().id());
                line.put("text", entry.document().text());
                ObjectNode metadata = line.putObject("metadata");
                entry.document().metadata().forEach(metadata::put);
                ArrayNode vector = line.putArray("vector");
                for (float value : entry.vector()) {
                    vector.add(value);
                }
                out.write(JSON.writeValueAsString(line));
                out.newLine();
            }
        }
    }

    public static InMemoryVectorStore load(Path file) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(file)) {
            String headerLine = in.readLine();
            if (headerLine == null) {
                throw new IOException("Leere Indexdatei: " + file);
            }
            InMemoryVectorStore store = new InMemoryVectorStore(JSON.readTree(headerLine).path("model").asText());

            String line;
            while ((line = in.readLine()) != null) {
                JsonNode node = JSON.readTree(line);
                Map<String, String> metadata = new HashMap<>();
                node.path("metadata").properties()
                        .forEach(field -> metadata.put(field.getKey(), field.getValue().asText()));
                JsonNode vectorNode = node.path("vector");
                float[] vector = new float[vectorNode.size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = vectorNode.get(i).floatValue();
                }
                store.add(new Document(node.path("id").asText(), node.path("text").asText(), metadata), vector);
            }
            return store;
        }
    }
}
