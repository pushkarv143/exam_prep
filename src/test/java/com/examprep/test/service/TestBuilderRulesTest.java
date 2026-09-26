package com.examprep.test.service;

import com.examprep.question.dto.QuestionRef;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;
import com.examprep.test.dto.BuilderDtos.GenerateFromPatternRequest;
import com.examprep.test.dto.TestDtos.ValidationReport;
import com.examprep.test.entity.SeriesStatus;
import com.examprep.test.entity.TestQuestion;
import com.examprep.test.entity.TestSection;
import com.examprep.test.entity.TestSeries;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure rules of the test builder: totals, difficulty split, publish validation. */
class TestBuilderRulesTest {

    private static TestQuestion tq(UUID section, UUID question, String marks) {
        TestQuestion t = new TestQuestion();
        t.setSectionId(section);
        t.setQuestionId(question);
        t.setMarks(new BigDecimal(marks));
        t.setNegativeMarks(BigDecimal.ONE);
        t.setQuestionVersion(1);
        return t;
    }

    private static TestSection section(String name, Integer maxAttempt) {
        TestSection s = new TestSection();
        s.setId(UUID.randomUUID());
        s.setName(name);
        s.setMaxQuestionsToAttempt(maxAttempt);
        return s;
    }

    @Test
    void attempt_any_n_counts_only_the_n_highest_marks() {
        TestSection s = section("B", 2);
        List<TestQuestion> tqs = List.of(tq(s.getId(), UUID.randomUUID(), "4"),
                tq(s.getId(), UUID.randomUUID(), "3"), tq(s.getId(), UUID.randomUUID(), "4"));
        assertThat(TestBuilderService.sectionMaxMarks(s, tqs)).isEqualByComparingTo("8");

        s.setMaxQuestionsToAttempt(null);
        assertThat(TestBuilderService.sectionMaxMarks(s, tqs)).isEqualByComparingTo("11");

        s.setMaxQuestionsToAttempt(10);                                   // more than available
        assertThat(TestBuilderService.sectionMaxMarks(s, tqs)).isEqualByComparingTo("11");
    }

    @Test
    void difficulty_split_always_sums_to_n() {
        GenerateFromPatternRequest mix = new GenerateFromPatternRequest(30, 50, 20, null, false, false);
        assertThat(TestGeneratorService.split(20, mix))
                .containsEntry(Difficulty.EASY, 6).containsEntry(Difficulty.MEDIUM, 10).containsEntry(Difficulty.HARD, 4);
        for (int n = 0; n <= 50; n++) {
            assertThat(TestGeneratorService.split(n, mix).values().stream().mapToInt(Integer::intValue).sum())
                    .as("n=%d", n).isEqualTo(n);
        }
        Map<Difficulty, Integer> odd = TestGeneratorService.split(7,
                new GenerateFromPatternRequest(33, 33, 34, null, false, false));
        assertThat(odd.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(7);
    }

    @Test
    void validator_separates_blocking_errors_from_warnings() {
        UUID physics = UUID.randomUUID();
        TestSection a = section("Physics A", null);
        a.setSubjectId(physics);
        a.setQuestionType(QuestionType.SINGLE_CORRECT);
        a.setTargetCount(3);
        TestSection empty = section("Empty", null);

        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        List<TestQuestion> tqs = List.of(tq(a.getId(), q1, "4"), tq(a.getId(), q2, "4"));
        Map<UUID, QuestionRef> refs = Map.of(
                q1, ref(q1, QuestionType.SINGLE_CORRECT, QuestionStatus.PUBLISHED, physics),
                q2, ref(q2, QuestionType.NUMERICAL, QuestionStatus.ARCHIVED, UUID.randomUUID()));
        TestSeries draftSeries = new TestSeries();
        draftSeries.setStatus(SeriesStatus.DRAFT);

        com.examprep.test.entity.Test test = new com.examprep.test.entity.Test();
        test.setDurationMinutes(60);
        ValidationReport report = new TestValidator().validate(test, draftSeries, List.of(a, empty), tqs, refs,
                Instant.now());

        assertThat(report.publishable()).isFalse();
        assertThat(report.errors()).anyMatch(e -> e.contains("'Empty' has no questions"))
                .anyMatch(e -> e.contains("is archived"))
                .anyMatch(e -> e.contains("requires SINGLE_CORRECT"));
        assertThat(report.warnings()).anyMatch(w -> w.contains("the pattern expects 3"))
                .anyMatch(w -> w.contains("different subject"))
                .anyMatch(w -> w.contains("series is still DRAFT"));
    }

    @Test
    void validator_accepts_a_complete_test() {
        TestSection s = section("Maths", null);
        UUID q = UUID.randomUUID();
        ValidationReport report = new TestValidator().validate(new com.examprep.test.entity.Test(), null,
                List.of(s), List.of(tq(s.getId(), q, "4")),
                Map.of(q, ref(q, QuestionType.NUMERICAL, QuestionStatus.PUBLISHED, null)), Instant.now());
        assertThat(report.publishable()).isTrue();
        assertThat(report.errors()).isEmpty();
    }

    private static QuestionRef ref(UUID id, QuestionType type, QuestionStatus status, UUID subject) {
        return ref(id, type, status, subject, 1);
    }

    private static QuestionRef ref(UUID id, QuestionType type, QuestionStatus status, UUID subject, int published) {
        return new QuestionRef(id, type, Difficulty.MEDIUM, status, subject, null, null, null,
                BigDecimal.valueOf(4), BigDecimal.ONE, published, published);
    }

    @Test
    void validator_warns_when_a_newer_question_version_is_published_and_blocks_unpublished_ones() {
        TestSection s = section("Physics", null);
        UUID newer = UUID.randomUUID();
        UUID never = UUID.randomUUID();
        Map<UUID, QuestionRef> refs = Map.of(
                newer, ref(newer, QuestionType.SINGLE_CORRECT, QuestionStatus.PUBLISHED, null, 3),
                never, new QuestionRef(never, QuestionType.SINGLE_CORRECT, Difficulty.MEDIUM, QuestionStatus.DRAFT,
                        null, null, null, null, BigDecimal.valueOf(4), BigDecimal.ONE, null, 1));
        ValidationReport report = new TestValidator().validate(new com.examprep.test.entity.Test(), null,
                List.of(s), List.of(tq(s.getId(), newer, "4"), tq(s.getId(), never, "4")), refs, Instant.now());
        assertThat(report.warnings()).anyMatch(w -> w.contains("uses v1; v3 is published"));
        assertThat(report.errors()).anyMatch(e -> e.contains("is not published"));
    }
}
