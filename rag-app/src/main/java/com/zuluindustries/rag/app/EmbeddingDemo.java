package com.zuluindustries.rag.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.EmbeddingModel;
import com.zuluindustries.rag.core.VectorMath;
import com.zuluindustries.rag.core.chunk.ChunkAssembler;
import com.zuluindustries.rag.embedding.openai.OpenAiCompatibleEmbeddingModel;

/**
 * Erstes Experiment mit echten Embeddings: Eine Frage und eine kleine Auswahl
 * von Chunks werden in Vektoren umgewandelt; dann werden die Chunks nach ihrer
 * Ähnlichkeit zur Frage sortiert.
 *
 * <p>Braucht die Umgebungsvariable {@value #API_KEY_VARIABLE} (Scaleway Secret Key).
 * Optionales Programm-Argument: eine eigene Frage.
 */
public class EmbeddingDemo {

    private static final String BASE_URL = "https://api.scaleway.ai/v1";
    private static final String MODEL = "bge-multilingual-gemma2";
    private static final String API_KEY_VARIABLE = "SCW_SECRET_KEY";

    private static final String DEFAULT_QUESTION = "Darf ich im Bunker vor dem Schlag den Sand berühren?";

    /** Auswahl aus ganz verschiedenen Teilen der Regeln (Regelnummer oder Definitionsbegriff). */
    private static final List<String> SAMPLE_NUMBERS = List.of(
            "12.2b",        // Einschränkungen, den Bunkersand zu berühren
            "Bunker",       // Definition
            "13.1c",        // Verbesserungen, die auf dem Grün erlaubt sind
            "4.1a",         // Für den Schlag zugelassene Schläger
            "18.2a",        // Wann Ball verloren oder im Aus ist
            "5.6a",         // Unangemessene Verzögerung des Spiels
            "16.1a",        // Wann Erleichterung zulässig ist (ungewöhnliche Platzverhältnisse)
            "Grundstrafe"); // Definition

    public static void main(String[] args) {
        String question = args.length > 0 ? String.join(" ", args) : DEFAULT_QUESTION;

        List<Document> chunks = GolfRules.process(GolfRules.DEFAULT_PDF).chunks();
        List<Document> samples = SAMPLE_NUMBERS.stream()
                .map(number -> firstChunkWithNumber(chunks, number))
                .toList();

        EmbeddingModel model = OpenAiCompatibleEmbeddingModel.fromEnvironment(BASE_URL, API_KEY_VARIABLE, MODEL);

        // Frage und Chunks in EINER Anfrage: die Frage an Position 0, danach die Chunks.
        List<String> texts = new ArrayList<>();
        texts.add(question);
        samples.forEach(chunk -> texts.add(chunk.text()));
        List<float[]> vectors = model.embed(texts);
        float[] questionVector = vectors.getFirst();

        System.out.println("Frage: " + question);
        System.out.println("Modell: " + MODEL + ", Vektorlänge: " + questionVector.length);
        System.out.println();

        record Scored(double similarity, Document chunk) {
        }
        List<Scored> ranking = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            double similarity = VectorMath.cosineSimilarity(questionVector, vectors.get(i + 1));
            ranking.add(new Scored(similarity, samples.get(i)));
        }
        ranking.sort(Comparator.comparingDouble(Scored::similarity).reversed());

        for (Scored scored : ranking) {
            System.out.printf("%.3f  %s%n", scored.similarity(),
                    scored.chunk().metadata().get(ChunkAssembler.HEADING_KEY));
        }
    }

    private static Document firstChunkWithNumber(List<Document> chunks, String number) {
        return chunks.stream()
                .filter(chunk -> Objects.equals(number, chunk.metadata().get(ChunkAssembler.NUMBER_KEY)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Kein Chunk mit Nummer " + number + " gefunden"));
    }
}
