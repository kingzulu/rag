package com.zuluindustries.rag.app;

import java.io.IOException;
import java.util.List;

import com.zuluindustries.rag.core.Document;
import com.zuluindustries.rag.core.Indexer;
import com.zuluindustries.rag.store.memory.InMemoryVectorStore;

/**
 * Baut den Suchindex auf oder aktualisiert ihn: Alle Chunks der Golfregeln
 * werden eingebettet und mit ihren Vektoren gespeichert. Chunks, die sich seit
 * dem letzten Lauf nicht geändert haben, werden nicht erneut eingebettet.
 *
 * <p>Braucht die Umgebungsvariable SCW_SECRET_KEY (Scaleway Secret Key).
 */
public class IndexApp {

    public static void main(String[] args) throws IOException {
        List<Document> chunks = GolfRules.process(GolfRules.DEFAULT_PDF).chunks();
        System.out.println("Chunks aus dem PDF: " + chunks.size());

        InMemoryVectorStore store = SearchIndex.loadOrCreate();
        Indexer indexer = new Indexer(Scaleway.embeddingModel(), store);

        System.out.println("Indexiere … (beim ersten Mal dauert das etwa eine Minute)");
        long start = System.nanoTime();
        Indexer.Result result = indexer.index(chunks);
        long seconds = (System.nanoTime() - start) / 1_000_000_000;

        store.save(GolfRules.INDEX_FILE);

        System.out.println("Fertig nach " + seconds + " s: " + result.embedded() + " neu eingebettet, "
                + result.reused() + " wiederverwendet, " + result.removed() + " entfernt.");
        System.out.println("Suchindex: " + store.size() + " Einträge mit je " + store.dimensions() + " Werten");
        System.out.println("Gespeichert in: " + GolfRules.INDEX_FILE.toAbsolutePath().normalize());
    }
}
