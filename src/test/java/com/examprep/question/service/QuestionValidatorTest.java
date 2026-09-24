package com.examprep.question.service;

import com.examprep.common.exception.BusinessException;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent;
import com.examprep.question.model.QuestionContent.MatchItem;
import com.examprep.question.model.QuestionContent.Option;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionValidatorTest {

    private final QuestionValidator validator = new QuestionValidator();

    private static final List<Option> ABCD = List.of(
            new Option("A", "1", null), new Option("B", "2", null),
            new Option("C", "3", null), new Option("D", "4", null));

    private static QuestionContent choice(List<Option> options) {
        return new QuestionContent("What?", null, options, null, null, null, null);
    }

    private static AnswerKey options(String... ids) {
        return new AnswerKey(List.of(ids), null, null, null);
    }

    @Test
    void valid_single_correct() {
        assertThatCode(() -> validator.validate(QuestionType.SINGLE_CORRECT, choice(ABCD), options("b")))
                .doesNotThrowAnyException();
    }

    @Test
    void single_correct_rejects_two_answers() {
        assertThatThrownBy(() -> validator.validate(QuestionType.SINGLE_CORRECT, choice(ABCD), options("A", "B")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("exactly one");
    }

    @Test
    void answer_must_reference_an_existing_option() {
        assertThatThrownBy(() -> validator.validate(QuestionType.SINGLE_CORRECT, choice(ABCD), options("E")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("'E' is not one of the options");
    }

    @Test
    void duplicate_option_ids_are_rejected() {
        List<Option> dup = List.of(new Option("A", "x", null), new Option("a", "y", null));
        assertThatThrownBy(() -> validator.validate(QuestionType.MULTIPLE_CORRECT, choice(dup), options("A")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Duplicate option id");
    }

    @Test
    void option_needs_text_or_image() {
        List<Option> blank = List.of(new Option("A", "x", null), new Option("B", " ", null));
        assertThatThrownBy(() -> validator.validate(QuestionType.SINGLE_CORRECT, choice(blank), options("A")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("needs text or an image");

        List<Option> imageOnly = List.of(new Option("A", "x", null), new Option("B", null, "https://img/b.png"));
        assertThatCode(() -> validator.validate(QuestionType.SINGLE_CORRECT, choice(imageOnly), options("B")))
                .doesNotThrowAnyException();
    }

    @Test
    void multiple_correct_accepts_several_answers() {
        assertThatCode(() -> validator.validate(QuestionType.MULTIPLE_CORRECT, choice(ABCD), options("A", "C", "D")))
                .doesNotThrowAnyException();
    }

    @Test
    void numerical_requires_value_and_no_options() {
        QuestionContent stem = new QuestionContent("Find x", null, null, null, null, null, null);
        assertThatCode(() -> validator.validate(QuestionType.NUMERICAL, stem,
                new AnswerKey(null, new BigDecimal("2.5"), new BigDecimal("0.01"), null)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validate(QuestionType.NUMERICAL, stem, AnswerKey.EMPTY))
                .isInstanceOf(BusinessException.class).hasMessageContaining("needs 'value'");

        assertThatThrownBy(() -> validator.validate(QuestionType.NUMERICAL, choice(ABCD),
                new AnswerKey(null, BigDecimal.ONE, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("must not have options");
    }

    @Test
    void match_requires_complete_mapping_into_right_column() {
        QuestionContent content = new QuestionContent("Match", null, null, null,
                List.of(new MatchItem("P", "a"), new MatchItem("Q", "b")),
                List.of(new MatchItem("1", "x"), new MatchItem("2", "y")), null);

        assertThatCode(() -> validator.validate(QuestionType.MATCH, content,
                new AnswerKey(null, null, null, Map.of("P", "2", "Q", "1"))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validate(QuestionType.MATCH, content,
                new AnswerKey(null, null, null, Map.of("P", "2"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("every matchLeft item");

        assertThatThrownBy(() -> validator.validate(QuestionType.MATCH, content,
                new AnswerKey(null, null, null, Map.of("P", "2", "Q", "9"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("'9' is not in matchRight");
    }

    @Test
    void paragraph_needs_passage_but_no_answer() {
        QuestionContent passage = new QuestionContent(null, null, null, "A long passage...", null, null, null);
        assertThatCode(() -> validator.validate(QuestionType.PARAGRAPH, passage, AnswerKey.EMPTY))
                .doesNotThrowAnyException();

        QuestionContent empty = new QuestionContent("stem", null, null, null, null, null, null);
        assertThatThrownBy(() -> validator.validate(QuestionType.PARAGRAPH, empty, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("content.paragraph");
    }

    @Test
    void stem_text_is_required_for_answerable_types() {
        QuestionContent noText = new QuestionContent(" ", null, ABCD, null, null, null, null);
        assertThatThrownBy(() -> validator.validate(QuestionType.SINGLE_CORRECT, noText, options("A")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("content.text");
    }
}
