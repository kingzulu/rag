package com.zuluindustries.rag.core;

import java.util.List;

/**
 * Wandelt Texte in Vektoren ("Embeddings") um. Texte mit ähnlicher Bedeutung
 * ergeben ähnliche Vektoren – darauf beruht die semantische Suche.
 */
public interface EmbeddingModel {

    /**
     * @param texts die Texte, die umgewandelt werden sollen
     * @return je Text ein Vektor, in derselben Reihenfolge wie die Texte
     */
    List<float[]> embed(List<String> texts);
}
