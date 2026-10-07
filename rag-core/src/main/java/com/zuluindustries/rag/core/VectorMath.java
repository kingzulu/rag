package com.zuluindustries.rag.core;

/**
 * Rechnen mit Vektoren.
 */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * Kosinus-Ähnlichkeit zweier Vektoren: misst, wie sehr sie in dieselbe
     * Richtung zeigen – unabhängig von ihrer Länge.
     *
     * @return 1 = gleiche Richtung, 0 = rechtwinklig (nichts gemeinsam),
     *         -1 = entgegengesetzt
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vektoren unterschiedlich lang: " + a.length + " und " + b.length);
        }
        double dotProduct = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
