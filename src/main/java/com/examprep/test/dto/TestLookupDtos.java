package com.examprep.test.dto;

import com.examprep.test.entity.TestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only snapshots of a test for other modules (attempt engine, evaluation). No entities leak out. */
public final class TestLookupDtos {

    private TestLookupDtos() {
    }

    public record TestSnapshot(UUID id, UUID seriesId, UUID examId, String title, TestStatus status,
                               int durationMinutes, Instant startAt, Instant endAt, int maxAttempts,
                               boolean shuffleQuestions, boolean shuffleOptions, boolean showResultImmediately,
                               BigDecimal totalMarks, int totalQuestions) {
    }

    public record SectionSpec(UUID id, String name, UUID subjectId, String instructions, int displayOrder,
                              Integer maxQuestionsToAttempt) {
    }

    /** One question's placement and effective marking in the test. */
    public record QuestionSlot(UUID testQuestionId, UUID sectionId, UUID questionId, int displayOrder,
                               BigDecimal marks, BigDecimal negativeMarks, boolean partialMarking) {
    }

    /** Sections and slots, both in display order. */
    public record TestStructure(TestSnapshot test, List<SectionSpec> sections, List<QuestionSlot> slots) {
    }
}
