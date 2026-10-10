package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    void skipsChatModelWhenBestHitIsBelowMinScore() {
        Fakes.WordCountEmbeddingModel embeddings = new Fakes.WordCountEmbeddingModel();
        Fakes.MapVectorStore store = new Fakes.MapVectorStore();
        new Indexer(embeddings, store).index(List.of(new Document("bunker", "Bunker")));
        RecordingChatModel chat = new RecordingChatModel();
        AnswerGenerator generator = new AnswerGenerator(new Retriever(embeddings, store, ""), chat,
                AnswerGenerator.Settings.of("System", 1).withMinScore(0.5, "Keine Antwort."));

        // Enthält keines der Wörter Bunker/Grün/Ball → Ähnlichkeit 0
        AnswerGenerator.Answer offTopic = generator.answer("Wie koche ich Spaghetti?");

        assertEquals("Keine Antwort.", offTopic.text());
        assertFalse(offTopic.modelAsked());
        assertTrue(chat.received.isEmpty(), "Das Sprachmodell darf nicht gefragt werden");

        AnswerGenerator.Answer onTopic = generator.answer("Bunker?");

        assertTrue(onTopic.modelAsked());
        assertEquals(2, chat.received.size());
    }

    @Test
    void searchesWithRewrittenTextButAnswersOriginalQuestion() {
        Fakes.WordCountEmbeddingModel embeddings = new Fakes.WordCountEmbeddingModel();
        Fakes.MapVectorStore store = new Fakes.MapVectorStore();
        new Indexer(embeddings, store).index(List.of(
                new Document("regel-gruen", "Regeln für das Grün"),
                new Document("regel-ball", "Ball im Spiel")));
        RecordingChatModel chat = new RecordingChatModel();
        QueryRewriter addGreen = question -> Optional.of(question + " (Grün)");
        AnswerGenerator generator = new AnswerGenerator(new Retriever(embeddings, store, ""), chat,
                AnswerGenerator.Settings.of("System", 1).withRewriter(addGreen));

        AnswerGenerator.Answer answer = generator.answer("Was gilt hier?");

        assertEquals("Was gilt hier? (Grün)", answer.searchText());
        assertEquals("regel-gruen", answer.sources().getFirst().document().id());   // dank "(Grün)" gefunden
        assertTrue(chat.received.get(1).content().endsWith("Frage: Was gilt hier?"));   // Originalfrage
    }

    @Test
    void stopsWithoutSearchingWhenRewriterRecognizesOffTopicQuestion() {
        Fakes.WordCountEmbeddingModel embeddings = new Fakes.WordCountEmbeddingModel();
        Fakes.MapVectorStore store = new Fakes.MapVectorStore();
        new Indexer(embeddings, store).index(List.of(new Document("regel-bunker", "Bunker")));
        embeddings.embeddedTexts.clear();
        RecordingChatModel chat = new RecordingChatModel();
        QueryRewriter offTopic = question -> Optional.empty();   // "keine Regelfrage"
        AnswerGenerator generator = new AnswerGenerator(new Retriever(embeddings, store, ""), chat,
                AnswerGenerator.Settings.of("System", 1).withMinScore(0.5, "Keine Antwort.").withRewriter(offTopic));

        AnswerGenerator.Answer answer = generator.answer("Wie koche ich Spaghetti?");

        assertEquals("Keine Antwort.", answer.text());
        assertFalse(answer.modelAsked());
        assertTrue(answer.sources().isEmpty());
        assertTrue(embeddings.embeddedTexts.isEmpty(), "Es wird gar nicht erst gesucht");
        assertTrue(chat.received.isEmpty(), "Das Antwort-Modell wird nicht gefragt");
    }

    @Test
    void checksThresholdWithRewrittenSearchText() {
        Fakes.WordCountEmbeddingModel embeddings = new Fakes.WordCountEmbeddingModel();
        Fakes.MapVectorStore store = new Fakes.MapVectorStore();
        new Indexer(embeddings, store).index(List.of(new Document("regel-bunker", "Bunker")));
        RecordingChatModel chat = new RecordingChatModel();
        QueryRewriter toRuleLanguage = question -> Optional.of("Ball im Bunker");   // "übersetzt"
        AnswerGenerator generator = new AnswerGenerator(new Retriever(embeddings, store, ""), chat,
                AnswerGenerator.Settings.of("System", 1).withMinScore(0.5, "Keine Antwort.")
                        .withRewriter(toRuleLanguage));

        // Die Originalfrage enthält kein "Bunker" – erst der übersetzte Suchtext erreicht die Schwelle.
        AnswerGenerator.Answer answer = generator.answer("Mein Ball liegt im Sand neben dem Grün.");

        assertTrue(answer.modelAsked());
        assertEquals("Ball im Bunker", answer.searchText());
        assertTrue(chat.received.get(1).content().endsWith("Frage: Mein Ball liegt im Sand neben dem Grün."));
    }

    /** Chat-Attrappe: liefert nacheinander die vorgegebenen Antworten und merkt sich jedes Gespräch. */
    private static class ScriptedChatModel implements ChatModel {

        final List<List<ChatMessage>> calls = new ArrayList<>();
        private final List<String> replies;

        ScriptedChatModel(String... replies) {
            this.replies = List.of(replies);
        }

        @Override
        public String chat(List<ChatMessage> messages) {
            calls.add(List.copyOf(messages));
            return replies.get(calls.size() - 1);
        }
    }

    /** Generator mit einer Prüfung, die jede Antwort mit "7.3" beanstandet. */
    private static AnswerGenerator generatorWithCheck(ChatModel chat) {
        Fakes.WordCountEmbeddingModel embeddings = new Fakes.WordCountEmbeddingModel();
        Fakes.MapVectorStore store = new Fakes.MapVectorStore();
        new Indexer(embeddings, store).index(List.of(new Document("bunker", "Regel 12.2b Bunker")));
        AnswerCheck no73 = (answer, sources) -> answer.contains("7.3") ? List.of("7.3 steht in keiner Quelle")
                : List.of();
        return new AnswerGenerator(new Retriever(embeddings, store, ""), chat,
                AnswerGenerator.Settings.of("System", 1).withCheck(no73, "Bitte korrigieren:\n%s"));
    }

    @Test
    void asksOnceWhenCheckFindsNothing() {
        ScriptedChatModel chat = new ScriptedChatModel("Regel 12.2b [1]");

        AnswerGenerator.Answer answer = generatorWithCheck(chat).answer("Bunker?");

        assertEquals("Regel 12.2b [1]", answer.text());
        assertEquals(1, chat.calls.size());
        assertFalse(answer.corrected());
        assertTrue(answer.problems().isEmpty());
    }

    @Test
    void asksAgainWithProblemsInSameConversation() {
        ScriptedChatModel chat = new ScriptedChatModel("Regel 7.3 [1]", "Regel 12.2b [1]");

        AnswerGenerator.Answer answer = generatorWithCheck(chat).answer("Bunker?");

        assertEquals("Regel 12.2b [1]", answer.text());
        assertTrue(answer.corrected());
        assertEquals(List.of("7.3 steht in keiner Quelle"), answer.firstAttemptProblems());
        assertTrue(answer.problems().isEmpty());

        List<ChatMessage> second = chat.calls.get(1);
        assertEquals(4, second.size());   // System, Quellen + Frage, erste Antwort, Korrektur
        assertEquals(new ChatMessage(Role.ASSISTANT, "Regel 7.3 [1]"), second.get(2));
        assertEquals(new ChatMessage(Role.USER, "Bitte korrigieren:\n- 7.3 steht in keiner Quelle"), second.get(3));
    }

    @Test
    void deliversSecondAnswerWithProblemsWhenCorrectionFails() {
        ScriptedChatModel chat = new ScriptedChatModel("Regel 7.3 [1]", "Immer noch Regel 7.3 [1]");

        AnswerGenerator.Answer answer = generatorWithCheck(chat).answer("Bunker?");

        assertEquals("Immer noch Regel 7.3 [1]", answer.text());
        assertEquals(List.of("7.3 steht in keiner Quelle"), answer.problems());
        assertEquals(2, chat.calls.size(), "Nur ein Nachbesserungsversuch");
    }

    @Test
    void collectsProblemsOfAllChecks() {
        AnswerCheck first = (answer, sources) -> List.of("A");
        AnswerCheck second = (answer, sources) -> List.of("B", "C");

        assertEquals(List.of("A", "B", "C"), AnswerCheck.all(first, AnswerCheck.NONE, second).problems("x", List.of()));
    }

    @Test
    void chainsExpandersWithAndThen() {
        Document first = new Document("eins", "Eins");
        Document second = new Document("zwei", "Zwei");
        ContextExpander addFirst = hits -> List.of(hits.getFirst(), new SearchResult(first, Double.NaN));
        ContextExpander addSecond = hits -> {
            List<SearchResult> result = new ArrayList<>(hits);
            result.add(new SearchResult(second, Double.NaN));
            return result;
        };
        Document hit = new Document("treffer", "Treffer");

        List<SearchResult> expanded = addFirst.andThen(addSecond).expand(List.of(new SearchResult(hit, 0.5)));

        assertEquals(List.of("treffer", "eins", "zwei"), expanded.stream().map(s -> s.document().id()).toList());
    }

    @Test
    void usesPdfPageWhenPrintedPageIsMissing() {
        Document chunk = new Document("gruen", "Grün", Map.of("seite", "119"));

        String message = AnswerGenerator.buildUserMessage("Frage?", List.of(new SearchResult(chunk, 0.5)));

        assertTrue(message.contains("[1] Grün\n(Fundstelle: Seite 119)"));
    }
}
