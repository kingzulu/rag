package com.zuluindustries.rag.core.chunk;

import java.util.List;

import com.zuluindustries.rag.core.Chunker;
import com.zuluindustries.rag.core.Document;

/**
 * Die einfachste Strategie: Der ganze Bereich wird nur nach Größe geteilt.
 * Jeder Chunk trägt den Namen des Abschnitts als Überschrift.
 */
public class SizeChunker implements Chunker {

    private final String sectionName;
    private final int maxChars;

    public SizeChunker(String sectionName, int maxChars) {
        this.sectionName = sectionName;
        this.maxChars = maxChars;
    }

    @Override
    public List<Document> chunk(List<Document> pages) {
        ChunkAssembler assembler = new ChunkAssembler(sectionName, maxChars);
        assembler.add(sectionName, null, Line.fromPages(pages));
        return assembler.build();
    }
}
