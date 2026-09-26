package com.examprep.result.scoring;

import com.examprep.attempt.model.StudentAnswer;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.PartialRule;
import com.examprep.result.scoring.ScoringEngine.OutcomeType;
import com.examprep.result.scoring.ScoringEngine.QuestionSpec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Numerical answer ranges and partial-marking rules (content studio A2). */
class ScoringRulesA2Test {

    private static QuestionSpec q(QuestionType type, String marks, String negative, boolean partial, AnswerKey key) {
        return new QuestionSpec(UUID.randomUUID(), UUID.randomUUID(), 1, type, new BigDecimal(marks),
                new BigDecimal(negative), partial, key, null, null, null);
    }

    private static AnswerKey multi(PartialRule rule, String... ids) {
        return new AnswerKey(List.of(ids), null, null, null, null, null, rule);
    }

    private static StudentAnswer chose(String... ids) {
        return new StudentAnswer(List.of(ids), null, null);
    }

    private static StudentAnswer value(String v) {
        return new StudentAnswer(null, v, null);
    }

    @Test
    void numerical_range_accepts_both_ends_and_nothing_outside() {
        AnswerKey range = new AnswerKey(null, null, null, null, new BigDecimal("2.00"), new BigDecimal("2.05"), null);
        QuestionSpec spec = q(QuestionType.NUMERICAL, "4", "1", false, range);
        assertThat(ScoringEngine.score(spec, value("2")).outcome()).isEqualTo(OutcomeType.CORRECT);
        assertThat(ScoringEngine.score(spec, value("2.02")).outcome()).isEqualTo(OutcomeType.CORRECT);
        assertThat(ScoringEngine.score(spec, value("2.050")).outcome()).isEqualTo(OutcomeType.CORRECT);
        assertThat(ScoringEngine.score(spec, value("2.051")).marks()).isEqualByComparingTo("-1");
        assertThat(ScoringEngine.score(spec, value("1.99")).outcome()).isEqualTo(OutcomeType.INCORRECT);
        assertThat(ScoringEngine.score(spec, value("abc")).outcome()).isEqualTo(OutcomeType.INCORRECT);
    }

    @Test
    void jee_advanced_rule_is_the_default() {
        QuestionSpec spec = q(QuestionType.MULTIPLE_CORRECT, "4", "2", true, multi(null, "A", "B", "C"));
        assertThat(ScoringEngine.score(spec, chose("A", "B")).marks()).isEqualByComparingTo("2");
        assertThat(ScoringEngine.score(spec, chose("A")).marks()).isEqualByComparingTo("1");
        assertThat(ScoringEngine.score(spec, chose("A", "D")).marks()).isEqualByComparingTo("-2");
    }

    @Test
    void proportional_rule_scales_by_the_number_of_correct_options() {
        QuestionSpec spec = q(QuestionType.MULTIPLE_CORRECT, "4", "2", true,
                multi(PartialRule.PROPORTIONAL, "A", "B", "C"));
        assertThat(ScoringEngine.score(spec, chose("A", "B")).marks()).isEqualByComparingTo("2.67");
        assertThat(ScoringEngine.score(spec, chose("C")).marks()).isEqualByComparingTo("1.33");
        assertThat(ScoringEngine.score(spec, chose("A", "B", "C")).outcome()).isEqualTo(OutcomeType.CORRECT);
    }

    @Test
    void none_rule_is_all_or_nothing_even_when_the_test_enables_partial_marking() {
        QuestionSpec spec = q(QuestionType.MULTIPLE_CORRECT, "4", "2", true, multi(PartialRule.NONE, "A", "B"));
        assertThat(ScoringEngine.score(spec, chose("A")).marks()).isEqualByComparingTo("-2");
        assertThat(ScoringEngine.score(spec, chose("A", "B")).marks()).isEqualByComparingTo("4");
    }

    @Test
    void test_level_switch_still_disables_partial_marking() {
        QuestionSpec spec = q(QuestionType.MULTIPLE_CORRECT, "4", "2", false,
                multi(PartialRule.PROPORTIONAL, "A", "B"));
        assertThat(ScoringEngine.score(spec, chose("A")).outcome()).isEqualTo(OutcomeType.INCORRECT);
    }
}
