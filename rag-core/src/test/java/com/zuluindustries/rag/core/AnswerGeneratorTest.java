package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.zuluindustries.rag.core.ChatMessage.Role;

class AnswerGeneratorTest {

    /** Chat-Modell-Attrappe: merkt sich die Nachrichten und antwortet immer gleich. */
    private static class RecordingChatModel implements ChatModel {

        final List<ChatMessage> received = new ArrayList<>();

        @Override
        public String chat(List<ChatMessage> messages) {
            received.addAll(messages);
            return "Nein, das ist nicht erlaubt [1].";
        }
    }

    @Test
    void passesNumberedSourcesAndQuestionToChatModel() {
        Fakes.WordCountEmbeddingModel embeddings = new Fakes.WordCountEmbeddingModel();
        Fakes.MapVectorStore store = new Fakes.MapVectorStore();
        new Indexer(embeddings, store).index(List.of(
                new Document("bunker", "Regel 12 › 12.2b Sand berühren\n\nIm Bunker gilt …",
                        Map.of("seite", "115", "seite_gedruckt", "113")),
                new Document("gruen", "Regel 13 › 13.1c Grün\n\nAuf dem Grün gilt …", Map.of("seite", "119"))));
        RecordingChatModel chat = new RecordingChatModel();
        AnswerGenerator generator = new AnswerGenerator(new Retriever(embeddings, store, ""), chat,
                "Antworte nur aus den Quellen.", 1);

        AnswerGenerator.Answer answer = generator.answer("Darf ich im Bunker den Sand berühren?");

        assertEquals("Nein, das ist nicht erlaubt [1].", answer.text());
        assertEquals("bunker", answer.sources().getFirst().document().id());

        assertEquals(2, chat.received.size());
        assertEquals(new ChatMessage(Role.SYSTEM, "Antworte nur aus den Quellen."), chat.received.get(0));
        assertEquals(Role.USER, chat.received.get(1).role());
        assertEquals("""
                Quellen:

                [1] Regel 12 › 12.2b Sand berühren

                Im Bunker gilt …
                (Fundstelle: Seite 113)

                Frage: Darf ich im Bunker den Sand berühren?""", chat.received.get(1).content());
    }

    @Test
    void passesExpandedSourcesToChatModel() {
        Fakes.WordCountEmbeddingModel embeddings = new Fakes.WordCountEmbeddingModel();
        Fakes.MapVectorStore store = new Fakes.MapVectorStore();
        new Indexer(embeddings, store).index(List.of(new Document("bunker", "Bunker")));
        Document extra = new Document("ergaenzt", "Ergänzte Quelle");
        ContextExpander addOne = hits -> List.of(hits.getFirst(), new SearchResult(extra, Double.NaN));
        RecordingChatModel chat = new RecordingChatModel();

        AnswerGenerator.Answer answer = new AnswerGenerator(new Retriever(embeddings, store, ""), chat,
                "System", 1, addOne).answer("Bunker?");

        assertEquals(List.of("bunker", "ergaenzt"), answer.sources().stream().map(s -> s.document().id()).toList());
        assertTrue(chat.received.get(1).content().contains("[2] Ergänzte Quelle"));
    }

    @Test
    void usesPdfPageWhenPrintedPageIsMissing() {
        Document chunk = new Document("gruen", "Grün", Map.of("seite", "119"));

        String message = AnswerGenerator.buildUserMessage("Frage?", List.of(new SearchResult(chunk, 0.5)));

        assertTrue(message.contains("[1] Grün\n(Fundstelle: Seite 119)"));
    }
}
