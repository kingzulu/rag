package com.zuluindustries.rag.app;

import com.zuluindustries.rag.core.AnswerCheck;
import com.zuluindustries.rag.core.AnswerGenerator;
import com.zuluindustries.rag.core.ChatModel;
import com.zuluindustries.rag.core.ChatQueryRewriter;
import com.zuluindustries.rag.core.CitationChecker;
import com.zuluindustries.rag.core.ForbiddenTermCheck;
import com.zuluindustries.rag.core.Retriever;
import com.zuluindustries.rag.core.chunk.SiblingExpander;
import com.zuluindustries.rag.store.memory.InMemoryVectorStore;

/**
 * Baut den Golfregel-Assistenten zusammen – an einer Stelle, damit AskApp und
 * AnswerEvaluation garantiert dieselbe Konfiguration verwenden:
 * <ol>
 * <li>Frage in eine hypothetische Regelstelle übersetzen ("HyDE"); erkennt das
 * Modell eine themenfremde Frage, ist Schluss.</li>
 * <li>Mit dem Absatz suchen – <b>ohne</b> Anfrage-Anweisung, weil er wie die
 * gespeicherten Chunks ein Text ist und keine Frage.</li>
 * <li>Schwelle als Sicherheitsnetz, Geschwister-Abschnitte ergänzen.</li>
 * <li>Die Originalfrage aus den Quellen beantworten.</li>
 * <li>Die Antwort prüfen: Jede Regelnummer muss in einer Quelle stehen, kein Begriff
 * aus den alten Regeln. Sonst einmal nachbessern lassen; bleibt etwas, wird die
 * Antwort mit Hinweis geliefert.</li>
 * </ol>
 */
final class GolfAssistant {

    private GolfAssistant() {
    }

    /**
     * @param answerModel das Chat-Modell für die Antworten (die Übersetzung nutzt
     *                    immer ein eigenes mit Temperatur 0)
     */
    static AnswerGenerator create(InMemoryVectorStore store, ChatModel answerModel) {
        Retriever passageRetriever = new Retriever(Scaleway.embeddingModel(), store, "");
        ChatQueryRewriter hypotheticalPassage = new ChatQueryRewriter(Scaleway.chatModel(0.0),
                GolfRules.HYPOTHETICAL_PASSAGE_PROMPT);
        SiblingExpander siblings = new SiblingExpander(store.documents(), GolfRules.MAX_CONTEXT_CHUNKS,
                GolfRules.MAX_CONTEXT_CHARS);
        AnswerCheck fromSourcesOnly = AnswerCheck.all(
                new CitationChecker(GolfRules.RULE_REFERENCE),
                new ForbiddenTermCheck(GolfRules.OUTDATED_TERMS, GolfRules.OUTDATED_TERM_LABEL));

        AnswerGenerator.Settings settings = AnswerGenerator.Settings.of(GolfRules.SYSTEM_PROMPT, GolfRules.ANSWER_TOP_K)
                .withRewriter(hypotheticalPassage)
                .withMinScore(Scaleway.MIN_ANSWER_SCORE, GolfRules.NO_ANSWER_TEXT)
                .withExpander(siblings)
                .withCheck(fromSourcesOnly, GolfRules.CORRECTION_PROMPT);
        return new AnswerGenerator(passageRetriever, answerModel, settings);
    }
}
