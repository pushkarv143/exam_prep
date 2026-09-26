package com.examprep.question.model;

import com.examprep.question.entity.CognitiveLevel;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.Question;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.entity.SourceType;
import com.examprep.question.model.QuestionContent.MatchItem;
import com.examprep.question.model.QuestionContent.Option;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything a question version fixes, stored as {@code question_versions.snapshot}.
 * The field names are part of the stored format (see the backfill in V6).
 */
public record QuestionSnapshot(
        QuestionType type,
        Difficulty difficulty,
        Language language,
        UUID examId,
        UUID subjectId,
        UUID chapterId,
        UUID topicId,
        String subTopic,
        UUID parentId,
        QuestionContent content,
        AnswerKey answerKey,
        QuestionTranslations translations,
        BigDecimal marks,
        BigDecimal negativeMarks,
        SourceType sourceType,
        String source,
        Short year,
        String pyqShift,
        Integer expectedTimeSec,
        CognitiveLevel cognitiveLevel,
        List<String> tags,
        List<String> concepts) {

    public QuestionSnapshot {
        translations = translations == null ? QuestionTranslations.NONE : translations;
        answerKey = answerKey == null ? AnswerKey.EMPTY : answerKey;
        tags = tags == null ? List.of() : List.copyOf(tags);
        concepts = concepts == null ? List.of() : List.copyOf(concepts);
        // 4.00 (from the database) and 4 (from a request) are the same mark
        marks = marks == null ? null : marks.stripTrailingZeros();
        negativeMarks = negativeMarks == null ? null : negativeMarks.stripTrailingZeros();
    }

    public static QuestionSnapshot of(Question q) {
        return new QuestionSnapshot(q.getType(), q.getDifficulty(), q.getLanguage(), q.getExamId(), q.getSubjectId(),
                q.getChapterId(), q.getTopicId(), q.getSubTopic(), q.getParentId(), q.getContent(), q.getAnswerKey(),
                q.getTranslations(), q.getDefaultMarks(), q.getDefaultNegativeMarks(), q.getSourceType(), q.getSource(),
                q.getYear(), q.getPyqShift(), q.getExpectedTimeSec() == null ? null : q.getExpectedTimeSec().intValue(),
                q.getCognitiveLevel(), q.getTags().stream().sorted().toList(),
                q.getConcepts().stream().sorted().toList());
    }

    /**
     * True when every possible answer scores the same under both versions: same type,
     * same answer key (including range and partial rule) and the same option / match ids.
     * Only such a version may replace the pinned version of a test that students may
     * already have attempted.
     */
    public boolean scoresLike(QuestionSnapshot other) {
        return other != null
                && type == other.type
                && Objects.equals(answerKey, other.answerKey)
                && optionIds(content).equals(optionIds(other.content))
                && matchIds(content, true).equals(matchIds(other.content, true))
                && matchIds(content, false).equals(matchIds(other.content, false));
    }

    private static List<String> optionIds(QuestionContent c) {
        return c == null ? List.of() : c.options().stream().map(Option::id).map(String::toUpperCase).sorted().toList();
    }

    private static List<String> matchIds(QuestionContent c, boolean left) {
        if (c == null) {
            return List.of();
        }
        return (left ? c.matchLeft() : c.matchRight()).stream().map(MatchItem::id).map(String::toUpperCase)
                .sorted().toList();
    }
}
