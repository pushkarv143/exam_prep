package com.examprep.attempt.paper;

import com.examprep.question.dto.StudentQuestionView.PassageView;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.QuestionContent.MatchItem;
import com.examprep.question.model.QuestionContent.Media;
import com.examprep.question.model.QuestionContent.Option;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The student-facing question paper. It is built once per test, cached, and then
 * shuffled per attempt. It contains <b>no answer keys and no solutions</b>. Its source
 * type ({@code StudentQuestionView}) has no such fields.
 *
 * @param passages shared paragraph texts, keyed by the paragraph id that questions reference
 */
public record PaperDto(UUID testId, String title, int durationMinutes, BigDecimal totalMarks, int totalQuestions,
                       List<PaperSection> sections, Map<UUID, PassageView> passages) {

    public record PaperSection(UUID id, String name, UUID subjectId, String instructions,
                               Integer maxQuestionsToAttempt, List<PaperQuestion> questions) {
    }

    /**
     * @param number     1-based number shown in the palette (renumbered after shuffling)
     * @param options    in display order. Ids are the canonical labels answers are stored against,
     *                   so a shuffled display never changes what gets saved.
     */
    public record PaperQuestion(UUID questionId, int number, QuestionType type, BigDecimal marks,
                                BigDecimal negativeMarks, boolean partialMarking, UUID paragraphId, String text,
                                List<Media> images, List<Option> options, List<MatchItem> matchLeft,
                                List<MatchItem> matchRight) {

        PaperQuestion withNumberAndOptions(int newNumber, List<Option> newOptions) {
            return new PaperQuestion(questionId, newNumber, type, marks, negativeMarks, partialMarking, paragraphId,
                    text, images, newOptions, matchLeft, matchRight);
        }
    }
}
