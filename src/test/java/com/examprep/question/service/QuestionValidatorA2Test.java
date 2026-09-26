package com.examprep.question.service;

import com.examprep.common.exception.BusinessException;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.NumericFormat;
import com.examprep.question.model.PartialRule;
import com.examprep.question.model.QuestionContent;
import com.examprep.question.model.QuestionContent.Option;
import com.examprep.question.model.QuestionTranslation;
import com.examprep.question.model.QuestionTranslations;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validation of the A2 additions: ranges, numeric format, partial rules, pinned options, translations. */
class QuestionValidatorA2Test {

    private final QuestionValidator validator = new QuestionValidator();

    private static final List<Option> ABCD = List.of(new Option("A", "1", null), new Option("B", "2", null),
            new Option("C", "3", null), new Option("D", "None of these", null, true));

    private static QuestionContent numerical(NumericFormat format) {
        return new QuestionContent("How much?", null, null, null, null, null, null, null, format);
    }

    private static AnswerKey range(String min, String max) {
        return new AnswerKey(null, null, null, null, min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max), null);
    }

    @Test
    void numerical_range_needs_both_ends_in_order() {
        assertThatCode(() -> validator.validate(QuestionType.NUMERICAL, numerical(null), range("2.00", "2.05")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(QuestionType.NUMERICAL, numerical(null), range("2", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("both 'min' and 'max'");
        assertThatThrownBy(() -> validator.validate(QuestionType.NUMERICAL, numerical(null), range("3", "2")))
                .hasMessageContaining("must not be greater");
        AnswerKey both = new AnswerKey(null, BigDecimal.ONE, null, null, BigDecimal.ZERO, BigDecimal.TEN, null);
        assertThatThrownBy(() -> validator.validate(QuestionType.NUMERICAL, numerical(null), both))
                .hasMessageContaining("not both");
    }

    @Test
    void integer_questions_need_whole_answers_without_tolerance() {
        AnswerKey whole = new AnswerKey(null, new BigDecimal("20.0"), null, null);
        assertThatCode(() -> validator.validate(QuestionType.NUMERICAL, numerical(NumericFormat.INTEGER), whole))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(QuestionType.NUMERICAL, numerical(NumericFormat.INTEGER),
                new AnswerKey(null, new BigDecimal("2.5"), null, null))).hasMessageContaining("whole-number answer");
        assertThatThrownBy(() -> validator.validate(QuestionType.NUMERICAL, numerical(NumericFormat.INTEGER),
                new AnswerKey(null, BigDecimal.TEN, BigDecimal.ONE, null))).hasMessageContaining("remove the tolerance");
    }

    @Test
    void partial_rule_and_pinned_options_are_choice_only() {
        QuestionContent choice = new QuestionContent("Pick", null, ABCD, null, null, null, null);
        assertThatCode(() -> validator.validate(QuestionType.MULTIPLE_CORRECT, choice,
                new AnswerKey(List.of("A", "B"), null, null, null, null, null, PartialRule.PROPORTIONAL)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(QuestionType.SINGLE_CORRECT, choice,
                new AnswerKey(List.of("A"), null, null, null, null, null, PartialRule.NONE)))
                .hasMessageContaining("only to MULTIPLE_CORRECT");
        assertThatThrownBy(() -> validator.validate(QuestionType.NUMERICAL,
                new QuestionContent("x", null, null, null, null, null, null, false, null),
                new AnswerKey(null, BigDecimal.ONE, null, null))).hasMessageContaining("only to choice questions");
        assertThatThrownBy(() -> validator.validate(QuestionType.SINGLE_CORRECT,
                new QuestionContent("x", null, ABCD, null, null, null, null, null, NumericFormat.INTEGER),
                new AnswerKey(List.of("A"), null, null, null))).hasMessageContaining("only to NUMERICAL");
    }

    @Test
    void translations_may_only_name_existing_options() {
        QuestionContent choice = new QuestionContent("Pick", null, ABCD, null, null, null, null);
        QuestionTranslations ok = QuestionTranslations.of(Map.of(Language.HI,
                new QuestionTranslation("चुनें", null, Map.of("a", "एक"), null, null, null)));
        assertThatCode(() -> validator.validateTranslations(QuestionType.SINGLE_CORRECT, choice, Language.EN, ok))
                .doesNotThrowAnyException();
        QuestionTranslations bad = QuestionTranslations.of(Map.of(Language.HI,
                new QuestionTranslation("चुनें", null, Map.of("E", "पाँच"), null, null, null)));
        assertThatThrownBy(() -> validator.validateTranslations(QuestionType.SINGLE_CORRECT, choice, Language.EN, bad))
                .hasMessageContaining("option E");
    }
}
