package com.examprep.result.scoring;

import com.examprep.attempt.entity.AnswerState;
import com.examprep.attempt.model.StudentAnswer;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.result.model.ScoreBreakdowns.SectionScore;
import com.examprep.result.scoring.ScoringEngine.Evaluation;
import com.examprep.result.scoring.ScoringEngine.OutcomeType;
import com.examprep.result.scoring.ScoringEngine.QuestionSpec;
import com.examprep.result.scoring.ScoringEngine.Response;
import com.examprep.result.scoring.ScoringEngine.SectionSpec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringEngineTest {

    static final UUID SECTION = UUID.randomUUID();
    static final UUID TOPIC_A = UUID.randomUUID();
    static final UUID TOPIC_B = UUID.randomUUID();

    private static QuestionSpec q(int order, QuestionType type, String marks, String negative, boolean partial,
                                  AnswerKey key, UUID topic) {
        return new QuestionSpec(new UUID(0, order), SECTION, order, type, new BigDecimal(marks), new BigDecimal(negative),
                partial, key, null, null, topic);
    }

    private static AnswerKey options(String... ids) {
        return new AnswerKey(List.of(ids), null, null, null);
    }

    private static StudentAnswer chose(String... ids) {
        return new StudentAnswer(List.of(ids), null, null);
    }

    private static Response answered(QuestionSpec q, StudentAnswer a) {
        return new Response(q.questionId(), a, AnswerState.ANSWERED, 30);
    }

    private static BigDecimal score(QuestionSpec q, StudentAnswer a) {
        return ScoringEngine.score(q, a).marks();
    }

    @Nested
    class SingleCorrect {
        final QuestionSpec scq = q(1, QuestionType.SINGLE_CORRECT, "4", "1", false, options("B"), TOPIC_A);

        @Test
        void correct_gets_full_marks_and_wrong_is_negative() {
            assertThat(score(scq, chose("B"))).isEqualByComparingTo("4");
            assertThat(score(scq, chose("b"))).isEqualByComparingTo("4");
            assertThat(score(scq, chose("A"))).isEqualByComparingTo("-1");
        }
    }

    @Nested
    class MultipleCorrectJeeAdvanced {
        /** Four options, three correct (A, B, D), +4 / -2, partial marking on. */
        final QuestionSpec mcq = q(1, QuestionType.MULTIPLE_CORRECT, "4", "2", true, options("A", "B", "D"), TOPIC_A);

        @Test
        void exactly_the_correct_set_scores_full() {
            assertThat(ScoringEngine.score(mcq, chose("D", "A", "B")).outcome()).isEqualTo(OutcomeType.CORRECT);
            assertThat(score(mcq, chose("A", "B", "D"))).isEqualByComparingTo("4");
        }

        @Test
        void partial_scores_one_per_correct_option_chosen() {
            assertThat(score(mcq, chose("A", "B"))).isEqualByComparingTo("2");
            assertThat(score(mcq, chose("D"))).isEqualByComparingTo("1");
            assertThat(ScoringEngine.score(mcq, chose("A")).outcome()).isEqualTo(OutcomeType.PARTIAL);
        }

        @Test
        void any_wrong_option_is_negative_even_with_correct_ones() {
            assertThat(score(mcq, chose("A", "B", "C"))).isEqualByComparingTo("-2");
            assertThat(score(mcq, chose("C"))).isEqualByComparingTo("-2");
        }

        @Test
        void without_partial_marking_an_incomplete_answer_is_wrong() {
            QuestionSpec strict = q(1, QuestionType.MULTIPLE_CORRECT, "4", "1", false, options("A", "B"), TOPIC_A);
            assertThat(score(strict, chose("A"))).isEqualByComparingTo("-1");
            assertThat(score(strict, chose("A", "B"))).isEqualByComparingTo("4");
        }
    }

    @Nested
    class Numerical {
        @Test
        void exact_match_when_no_tolerance() {
            QuestionSpec num = q(1, QuestionType.NUMERICAL, "4", "0", false,
                    new AnswerKey(null, new BigDecimal("20"), null, null), TOPIC_A);
            assertThat(score(num, new StudentAnswer(null, "20", null))).isEqualByComparingTo("4");
            assertThat(score(num, new StudentAnswer(null, "20.00", null))).isEqualByComparingTo("4");
            assertThat(score(num, new StudentAnswer(null, "20.01", null))).isEqualByComparingTo("0");   // no negative
        }

        @Test
        void tolerance_is_inclusive_and_garbage_is_wrong() {
            AnswerKey key = new AnswerKey(null, new BigDecimal("0.5"), new BigDecimal("0.01"), null);
            QuestionSpec num = q(1, QuestionType.NUMERICAL, "4", "1", false, key, TOPIC_A);
            assertThat(score(num, new StudentAnswer(null, "0.51", null))).isEqualByComparingTo("4");
            assertThat(score(num, new StudentAnswer(null, "0.49", null))).isEqualByComparingTo("4");
            assertThat(score(num, new StudentAnswer(null, "0.52", null))).isEqualByComparingTo("-1");
            assertThat(ScoringEngine.numericalMatches("abc", key)).isFalse();
        }
    }

    @Test
    void match_needs_every_pair_right() {
        QuestionSpec m = q(1, QuestionType.MATCH, "3", "1", false,
                new AnswerKey(null, null, null, Map.of("P", "2", "Q", "1")), TOPIC_A);
        assertThat(score(m, new StudentAnswer(null, null, Map.of("p", "2", "q", "1")))).isEqualByComparingTo("3");
        assertThat(score(m, new StudentAnswer(null, null, Map.of("P", "2", "Q", "2")))).isEqualByComparingTo("-1");
    }

    @Test
    void full_paper_totals_nta_states_and_breakdowns() {
        QuestionSpec q1 = q(1, QuestionType.SINGLE_CORRECT, "4", "1", false, options("A"), TOPIC_A);
        QuestionSpec q2 = q(2, QuestionType.SINGLE_CORRECT, "4", "1", false, options("B"), TOPIC_A);
        QuestionSpec q3 = q(3, QuestionType.SINGLE_CORRECT, "4", "1", false, options("C"), TOPIC_B);
        QuestionSpec q4 = q(4, QuestionType.SINGLE_CORRECT, "4", "1", false, options("D"), TOPIC_B);
        QuestionSpec q5 = q(5, QuestionType.SINGLE_CORRECT, "4", "1", false, options("A"), TOPIC_B);

        Map<UUID, Response> responses = new HashMap<>();
        responses.put(q1.questionId(), answered(q1, chose("A")));                                        // +4
        responses.put(q2.questionId(), new Response(q2.questionId(), chose("B"), AnswerState.ANSWERED_AND_MARKED, 10)); // +4 (evaluated)
        responses.put(q3.questionId(), answered(q3, chose("A")));                                        // -1
        responses.put(q4.questionId(), new Response(q4.questionId(), null, AnswerState.MARKED_FOR_REVIEW, 5));  // 0, unattempted
        // q5 never visited                                                                               // 0

        Evaluation ev = ScoringEngine.evaluate(List.of(new SectionSpec(SECTION, "Physics", null, null,
                new BigDecimal("20"))), List.of(q1, q2, q3, q4, q5), responses);

        assertThat(ev.score()).isEqualByComparingTo("7");
        assertThat(ev.correct()).isEqualTo(2);
        assertThat(ev.incorrect()).isEqualTo(1);
        assertThat(ev.unattempted()).isEqualTo(2);
        assertThat(ev.accuracy()).isEqualByComparingTo("66.67");        // 2 of 3 attempted
        assertThat(ev.timeSpentSeconds()).isEqualTo(30 + 10 + 30 + 5);

        SectionScore s = ev.sections().getFirst();
        assertThat(s.score()).isEqualByComparingTo("7");
        assertThat(s.maxScore()).isEqualByComparingTo("20");
        assertThat(s.attempted()).isEqualTo(3);

        assertThat(ev.topics()).hasSize(2);
        assertThat(ev.topics()).filteredOn(t -> t.topicId().equals(TOPIC_A)).singleElement()
                .satisfies(t -> {
                    assertThat(t.correct()).isEqualTo(2);
                    assertThat(t.score()).isEqualByComparingTo("8");
                    assertThat(t.maxScore()).isEqualByComparingTo("8");
                });
        assertThat(ev.topics()).filteredOn(t -> t.topicId().equals(TOPIC_B)).singleElement()
                .satisfies(t -> {
                    assertThat(t.total()).isEqualTo(3);
                    assertThat(t.attempted()).isEqualTo(1);
                    assertThat(t.score()).isEqualByComparingTo("-1");
                });
    }

    @Test
    void attempt_any_n_counts_only_the_first_n_answered_in_question_order() {
        // JEE Main Section B: attempt any 5 of 10. The student answered 6; the 6th (q6) is ignored.
        List<QuestionSpec> qs = new java.util.ArrayList<>();
        Map<UUID, Response> responses = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            QuestionSpec q = q(i, QuestionType.NUMERICAL, "4", "0", false,
                    new AnswerKey(null, BigDecimal.valueOf(i), null, null), TOPIC_A);
            qs.add(q);
            if (i <= 6) {
                responses.put(q.questionId(), answered(q, new StudentAnswer(null, String.valueOf(i), null)));   // all correct
            }
        }
        // Answer order in the map is irrelevant: question order decides.
        Evaluation ev = ScoringEngine.evaluate(List.of(new SectionSpec(SECTION, "B", null, 5, new BigDecimal("20"))),
                qs, responses);

        assertThat(ev.score()).isEqualByComparingTo("20");
        assertThat(ev.correct()).isEqualTo(5);
        assertThat(ev.outcomes()).filteredOn(o -> o.questionId().equals(new UUID(0, 6))).singleElement()
                .extracting(ScoringEngine.Outcome::outcome).isEqualTo(OutcomeType.UNATTEMPTED);
    }

    @Test
    void empty_attempt_scores_zero_with_zero_accuracy() {
        QuestionSpec q1 = q(1, QuestionType.SINGLE_CORRECT, "4", "1", false, options("A"), TOPIC_A);
        Evaluation ev = ScoringEngine.evaluate(List.of(new SectionSpec(SECTION, "S", null, null, BigDecimal.valueOf(4))),
                List.of(q1), Map.of());
        assertThat(ev.score()).isEqualByComparingTo("0");
        assertThat(ev.accuracy()).isEqualByComparingTo("0");
        assertThat(ev.unattempted()).isEqualTo(1);
    }
}
