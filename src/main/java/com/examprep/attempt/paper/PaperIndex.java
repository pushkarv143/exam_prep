package com.examprep.attempt.paper;

import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.QuestionContent.MatchItem;
import com.examprep.question.model.QuestionContent.Option;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lookup structure derived from a paper. Autosave validates every change against it
 * in memory (question belongs to the test, option ids exist, section limits) without
 * any I/O.
 */
public record PaperIndex(Map<UUID, Entry> questions, Map<UUID, Integer> sectionLimits) {

    public record Entry(UUID sectionId, QuestionType type, Set<String> optionIds, Set<String> leftIds,
                        Set<String> rightIds) {
    }

    public static PaperIndex of(PaperDto paper) {
        Map<UUID, Entry> questions = new HashMap<>();
        Map<UUID, Integer> limits = new HashMap<>();
        for (PaperDto.PaperSection s : paper.sections()) {
            if (s.maxQuestionsToAttempt() != null) {
                limits.put(s.id(), s.maxQuestionsToAttempt());
            }
            for (PaperDto.PaperQuestion q : s.questions()) {
                questions.put(q.questionId(), new Entry(s.id(), q.type(),
                        q.options().stream().map(Option::id).map(String::toUpperCase).collect(Collectors.toSet()),
                        q.matchLeft().stream().map(MatchItem::id).map(String::toUpperCase).collect(Collectors.toSet()),
                        q.matchRight().stream().map(MatchItem::id).map(String::toUpperCase).collect(Collectors.toSet())));
            }
        }
        return new PaperIndex(Map.copyOf(questions), Map.copyOf(limits));
    }

    public Entry question(UUID questionId) {
        return questions.get(questionId);
    }
}
