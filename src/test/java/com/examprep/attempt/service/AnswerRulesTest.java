package com.examprep.attempt.service;

import com.examprep.attempt.entity.AnswerState;
import com.examprep.attempt.model.StudentAnswer;
import com.examprep.attempt.paper.PaperIndex;
import com.examprep.attempt.store.AttemptRedisStore.SavedAnswer;
import com.examprep.question.entity.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Answer normalisation, NTA state derivation and "attempt any N". */
class AnswerRulesTest {

    private final AnswerValidator validator = new AnswerValidator();
    private static final UUID SECTION = UUID.randomUUID();

    private static PaperIndex.Entry entry(QuestionType type) {
        return new PaperIndex.Entry(SECTION, type, Set.of("A", "B", "C", "D"), Set.of("P", "Q"), Set.of("1", "2"));
    }

    @Test
    void choice_answers_are_normalised_and_checked() {
        assertThat(validator.normalize(entry(QuestionType.MULTIPLE_CORRECT),
                new StudentAnswer(List.of("c", " a", "C"), null, null)).options()).containsExactly("A", "C");
        assertThatThrownBy(() -> validator.normalize(entry(QuestionType.SINGLE_CORRECT),
                new StudentAnswer(List.of("A", "B"), null, null))).hasMessage("Only one option can be selected");
        assertThatThrownBy(() -> validator.normalize(entry(QuestionType.SINGLE_CORRECT),
                new StudentAnswer(List.of("E"), null, null))).hasMessage("Unknown option 'E'");
    }

    @Test
    void empty_answer_means_clear_response() {
        assertThat(validator.normalize(entry(QuestionType.SINGLE_CORRECT), null)).isNull();
        assertThat(validator.normalize(entry(QuestionType.SINGLE_CORRECT), new StudentAnswer(List.of(), null, null)))
                .isNull();
        assertThat(validator.normalize(entry(QuestionType.NUMERICAL), new StudentAnswer(null, "  ", null))).isNull();
    }

    @Test
    void numerical_accepts_keypad_numbers_only() {
        assertThat(validator.normalize(entry(QuestionType.NUMERICAL), new StudentAnswer(null, " -2.50 ", null)).value())
                .isEqualTo("-2.50");
        for (String bad : List.of("1e5", "2,5", "abc", "1.2345678", "12345678901")) {
            assertThatThrownBy(() -> validator.normalize(entry(QuestionType.NUMERICAL),
                    new StudentAnswer(null, bad, null))).as(bad).hasMessage("Enter a valid number");
        }
    }

    @Test
    void match_pairs_must_use_known_items() {
        assertThat(validator.normalize(entry(QuestionType.MATCH), new StudentAnswer(null, null, Map.of("q", "1", "p", "2")))
                .pairs()).containsExactly(Map.entry("P", "2"), Map.entry("Q", "1"));
        assertThatThrownBy(() -> validator.normalize(entry(QuestionType.MATCH),
                new StudentAnswer(null, null, Map.of("P", "9")))).hasMessageContaining("Unknown match item");
    }

    @Test
    void nta_states_are_derived_not_trusted() {
        assertThat(AnswerState.of(true, false)).isEqualTo(AnswerState.ANSWERED);
        assertThat(AnswerState.of(true, true)).isEqualTo(AnswerState.ANSWERED_AND_MARKED);
        assertThat(AnswerState.of(false, true)).isEqualTo(AnswerState.MARKED_FOR_REVIEW);
        assertThat(AnswerState.of(false, false)).isEqualTo(AnswerState.NOT_ANSWERED);
        assertThat(AnswerState.ANSWERED_AND_MARKED.hasAnswer()).isTrue();
    }

    @Test
    void attempt_any_n_allows_swapping_but_not_exceeding() {
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID q3 = UUID.randomUUID();
        PaperIndex index = new PaperIndex(Map.of(q1, entry(QuestionType.NUMERICAL), q2, entry(QuestionType.NUMERICAL),
                q3, entry(QuestionType.NUMERICAL)), Map.of(SECTION, 2));
        Map<UUID, SavedAnswer> stored = Map.of(
                q1, new SavedAnswer(new StudentAnswer(null, "1", null), AnswerState.ANSWERED, 1, 0, 1),
                q2, new SavedAnswer(new StudentAnswer(null, "2", null), AnswerState.ANSWERED_AND_MARKED, 2, 0, 1));

        AutosaveService.SectionLimiter limiter = new AutosaveService.SectionLimiter(index, stored);
        assertThat(limiter.allow(q3, SECTION, true)).isFalse();    // third answer: over the limit
        assertThat(limiter.allow(q2, SECTION, true)).isTrue();     // re-answering an answered one is fine
        assertThat(limiter.allow(q1, SECTION, false)).isTrue();    // clear q1 ...
        assertThat(limiter.allow(q3, SECTION, true)).isTrue();     // ... which frees a slot for q3
        assertThat(limiter.allow(q1, SECTION, true)).isFalse();
    }

    /** Regression: {@code isEmpty()} once leaked into stored JSON as {"empty": false}. */
    @Test
    void student_answer_json_has_only_its_fields() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new StudentAnswer(List.of("B"), null, null));
        assertThat(json).isEqualTo("{\"options\":[\"B\"]}");
    }
}
