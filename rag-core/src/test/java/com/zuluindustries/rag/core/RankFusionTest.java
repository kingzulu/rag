package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class RankFusionTest {

    /** Trefferliste aus Dokument-IDs; der Ähnlichkeitswert spielt für RRF keine Rolle. */
    private static List<SearchResult> ranking(String... ids) {
        return Arrays.stream(ids).map(id -> new SearchResult(new Document(id, "Text " + id), 0.5)).toList();
    }

    private static List<String> ids(List<SearchResult> results) {
        return results.stream().map(result -> result.document().id()).toList();
    }

    @Test
    void documentInBothListsBeatsSingleFirstPlace() {
        List<SearchResult> fused = RankFusion.fuse(List.of(ranking("a", "b"), ranking("c", "b")), 60);

        assertEquals(List.of("b", "a", "c"), ids(fused));   // a und c gleichauf: a kam zuerst vor
    }

    @Test
    void addsReciprocalRanks() {
        List<SearchResult> fused = RankFusion.fuse(List.of(ranking("a", "b"), ranking("c", "b")), 60);

        assertEquals(1.0 / 62 + 1.0 / 62, fused.getFirst().score(), 1e-12);
        assertEquals(1.0 / 61, fused.get(1).score(), 1e-12);
    }

    @Test
    void countsDocumentOnlyOncePerList() {
        List<SearchResult> fused = RankFusion.fuse(List.of(ranking("a", "a", "b")), 60);

        assertEquals(List.of("a", "b"), ids(fused));
        assertEquals(1.0 / 61, fused.getFirst().score(), 1e-12);
    }

    @Test
    void handlesEmptyLists() {
        assertTrue(RankFusion.fuse(List.of(), 60).isEmpty());
        assertEquals(List.of("a"), ids(RankFusion.fuse(List.of(List.of(), ranking("a")), 60)));
    }
}
