package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VectorMathTest {

    private static final double PRECISION = 1e-9;

    @Test
    void identicalVectorsHaveSimilarityOne() {
        assertEquals(1.0, VectorMath.cosineSimilarity(new float[] { 1, 2, 3 }, new float[] { 1, 2, 3 }), PRECISION);
    }

    @Test
    void lengthDoesNotMatterOnlyDirection() {
        assertEquals(1.0, VectorMath.cosineSimilarity(new float[] { 1, 2, 3 }, new float[] { 2, 4, 6 }), PRECISION);
    }

    @Test
    void perpendicularVectorsHaveSimilarityZero() {
        assertEquals(0.0, VectorMath.cosineSimilarity(new float[] { 1, 0 }, new float[] { 0, 1 }), PRECISION);
    }

    @Test
    void oppositeVectorsHaveSimilarityMinusOne() {
        assertEquals(-1.0, VectorMath.cosineSimilarity(new float[] { 1, 2 }, new float[] { -1, -2 }), PRECISION);
    }

    @Test
    void rejectsVectorsOfDifferentLength() {
        assertThrows(IllegalArgumentException.class,
                () -> VectorMath.cosineSimilarity(new float[] { 1, 2 }, new float[] { 1, 2, 3 }));
    }
}
