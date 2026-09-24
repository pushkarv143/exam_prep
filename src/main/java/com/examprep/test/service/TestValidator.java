package com.examprep.test.service;

import com.examprep.question.dto.QuestionRef;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.test.dto.TestDtos.ValidationReport;
import com.examprep.test.entity.SeriesStatus;
import com.examprep.test.entity.Test;
import com.examprep.test.entity.TestQuestion;
import com.examprep.test.entity.TestSection;
import com.examprep.test.entity.TestSeries;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Publish-readiness rules. <b>Errors</b> block publishing (the paper would be broken or
 * unfair). <b>Warnings</b> are shown to the author but allowed (e.g. a JEE Main test
 * deliberately built with fewer questions).
 */
@Component
public class TestValidator {

    public ValidationReport validate(Test test, TestSeries series, List<TestSection> sections,
                                     List<TestQuestion> testQuestions, Map<UUID, QuestionRef> refs, Instant now) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (sections.isEmpty()) {
            errors.add("Test has no sections");
        }
        if (testQuestions.isEmpty()) {
            errors.add("Test has no questions");
        }
        if (test.getEndAt() != null && !test.getEndAt().isAfter(now)) {
            errors.add("End time is in the past");
        }
        if (series != null && series.getStatus() == SeriesStatus.ARCHIVED) {
            errors.add("The test series is archived");
        }
        if (series != null && series.getStatus() == SeriesStatus.DRAFT) {
            warnings.add("The test series is still DRAFT, so students will not see this test until it is published");
        }
        if (test.getStartAt() != null && test.getEndAt() != null
                && Duration.between(test.getStartAt(), test.getEndAt()).toMinutes() < test.getDurationMinutes()) {
            warnings.add("The window is shorter than the duration; everyone's attempt is cut off at the end time");
        }

        Map<UUID, List<TestQuestion>> bySection = testQuestions.stream()
                .collect(Collectors.groupingBy(TestQuestion::getSectionId));
        for (TestSection s : sections) {
            List<TestQuestion> tqs = bySection.getOrDefault(s.getId(), List.of());
            if (tqs.isEmpty()) {
                errors.add("Section '" + s.getName() + "' has no questions");
                continue;
            }
            if (s.getTargetCount() != null && tqs.size() != s.getTargetCount()) {
                warnings.add("Section '" + s.getName() + "' has " + tqs.size() + " questions; the pattern expects "
                        + s.getTargetCount());
            }
            if (s.getMaxQuestionsToAttempt() != null && s.getMaxQuestionsToAttempt() >= tqs.size()) {
                warnings.add("Section '" + s.getName() + "': 'attempt any " + s.getMaxQuestionsToAttempt()
                        + "' has no effect with " + tqs.size() + " questions");
            }
            for (TestQuestion tq : tqs) {
                QuestionRef ref = refs.get(tq.getQuestionId());
                if (ref == null) {
                    errors.add("Question " + tq.getQuestionId() + " no longer exists");
                    continue;
                }
                if (ref.status() != QuestionStatus.ACTIVE) {
                    errors.add("Question " + ref.id() + " in '" + s.getName() + "' is " + ref.status());
                }
                if (s.getQuestionType() != null && ref.type() != s.getQuestionType()) {
                    errors.add("Question " + ref.id() + " is " + ref.type() + " but '" + s.getName() + "' requires "
                            + s.getQuestionType());
                }
                if (s.getSubjectId() != null && !s.getSubjectId().equals(ref.subjectId())) {
                    warnings.add("Question " + ref.id() + " belongs to a different subject than '" + s.getName() + "'");
                }
                if (tq.getMarks().signum() == 0) {
                    warnings.add("Question " + ref.id() + " in '" + s.getName() + "' carries 0 marks");
                }
            }
        }
        return new ValidationReport(errors.isEmpty(), errors, warnings);
    }
}
