package com.examprep.question.service;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.NumericFormat;
import com.examprep.question.model.QuestionContent;
import com.examprep.question.model.QuestionContent.MatchItem;
import com.examprep.question.model.QuestionContent.Option;
import com.examprep.question.model.QuestionTranslation;
import com.examprep.question.model.QuestionTranslations;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cross-field rules that Bean Validation cannot express: whether the content, the answer
 * key and the translations agree for the given type. A question that passes here can
 * always be rendered and evaluated.
 */
@Component
public class QuestionValidator {

    static final int MIN_OPTIONS = 2;

    public void validate(QuestionType type, QuestionContent content, AnswerKey key) {
        boolean choice = type.hasOptions();
        require(choice || content.shuffleOptions() == null, "'shuffleOptions' applies only to choice questions");
        require(choice || content.options().stream().noneMatch(Option::keepsPosition),
                "Pinned options apply only to choice questions");
        require(type == QuestionType.NUMERICAL || content.numericFormat() == null,
                "'numericFormat' applies only to NUMERICAL questions");

        if (type == QuestionType.PARAGRAPH) {
            require(notBlank(content.paragraph()), "PARAGRAPH requires 'content.paragraph'");
            return;
        }
        require(notBlank(content.text()), "'content.text' is required");
        require(key != null, "'answerKey' is required for " + type);
        require(type == QuestionType.MULTIPLE_CORRECT || key.partial() == null,
                "A partial-marking rule applies only to MULTIPLE_CORRECT questions");
        require(type == QuestionType.NUMERICAL || (key.min() == null && key.max() == null),
                "An answer range applies only to NUMERICAL questions");

        switch (type) {
            case SINGLE_CORRECT, MULTIPLE_CORRECT -> validateChoice(type, content, key);
            case NUMERICAL -> validateNumerical(content, key);
            case MATCH -> validateMatch(content, key);
            default -> throw new IllegalStateException("Unhandled type " + type);
        }
    }

    /**
     * Translations share ids with the primary content, so every translated option or match
     * item must exist there. Missing translations are allowed (tracked as "incomplete").
     */
    public void validateTranslations(QuestionType type, QuestionContent content, Language primary,
                                     QuestionTranslations translations) {
        for (Map.Entry<Language, QuestionTranslation> e : translations.asMap().entrySet()) {
            if (e.getKey() == primary) {
                continue;
            }
            QuestionTranslation t = e.getValue();
            String lang = e.getKey().name();
            Set<String> optionIds = content.options().stream().map(o -> o.id().trim().toUpperCase())
                    .collect(Collectors.toSet());
            for (String id : t.options().keySet()) {
                require(optionIds.contains(id), lang + " translation has text for option " + id
                        + ", which the question does not have");
            }
            requireIds(t.matchLeft().keySet(), content.matchLeft(), lang, "column I");
            requireIds(t.matchRight().keySet(), content.matchRight(), lang, "column II");
            require(type == QuestionType.PARAGRAPH || t.paragraph() == null,
                    lang + " translation has a passage, but the question is not a PARAGRAPH");
        }
    }

    private void validateChoice(QuestionType type, QuestionContent content, AnswerKey key) {
        List<Option> options = content.options();
        require(options.size() >= MIN_OPTIONS, type + " needs at least " + MIN_OPTIONS + " options");
        Set<String> ids = new HashSet<>();
        for (Option o : options) {
            require(ids.add(o.id().trim().toUpperCase()), "Duplicate option id '" + o.id() + "'");
            require(notBlank(o.text()) || notBlank(o.image()), "Option " + o.id() + " needs text or an image");
        }
        require(!key.options().isEmpty(), "Answer key must list the correct option(s)");
        if (type == QuestionType.SINGLE_CORRECT) {
            require(key.options().size() == 1, "SINGLE_CORRECT must have exactly one correct option");
        }
        for (String answer : key.options()) {
            require(ids.contains(answer), "Correct option '" + answer + "' is not one of the options " + ids);
        }
        require(key.value() == null && key.pairs().isEmpty(), "Choice questions use only 'answerKey.options'");
    }

    private void validateNumerical(QuestionContent content, AnswerKey key) {
        require(content.options().isEmpty(), "NUMERICAL questions must not have options");
        require(key.options().isEmpty() && key.pairs().isEmpty(),
                "NUMERICAL uses only 'value'/'tolerance' or 'min'/'max'");
        boolean integer = content.numericFormat() == NumericFormat.INTEGER;
        if (key.hasRange()) {
            require(key.min() != null && key.max() != null, "An answer range needs both 'min' and 'max'");
            require(key.value() == null && key.tolerance() == null,
                    "Give either an exact value (with optional tolerance) or a range, not both");
            require(key.min().compareTo(key.max()) <= 0, "'min' must not be greater than 'max'");
            require(!integer || (isWhole(key.min()) && isWhole(key.max())),
                    "An integer-type question needs a whole-number range");
        } else {
            require(key.value() != null, "NUMERICAL answer key needs 'value' (or a 'min'/'max' range)");
            require(key.tolerance() == null || key.tolerance().signum() > 0, "'tolerance' must be positive");
            require(!integer || isWhole(key.value()), "An integer-type question needs a whole-number answer");
            require(!integer || key.tolerance() == null, "Integer-type answers are exact; remove the tolerance");
        }
    }

    private void validateMatch(QuestionContent content, AnswerKey key) {
        require(!content.matchLeft().isEmpty() && !content.matchRight().isEmpty(),
                "MATCH needs 'matchLeft' and 'matchRight'");
        Set<String> left = upperIds(content.matchLeft());
        Set<String> right = upperIds(content.matchRight());
        require(left.size() == content.matchLeft().size(), "Duplicate id in matchLeft");
        require(right.size() == content.matchRight().size(), "Duplicate id in matchRight");
        require(key.pairs().keySet().equals(left), "Answer key must map every matchLeft item exactly once");
        for (String target : key.pairs().values()) {
            require(right.contains(target), "Pair target '" + target + "' is not in matchRight " + right);
        }
    }

    private static void requireIds(Set<String> translated, List<MatchItem> items, String lang, String column) {
        Set<String> ids = upperIds(items);
        for (String id : translated) {
            require(ids.contains(id), lang + " translation has text for " + column + " item " + id
                    + ", which the question does not have");
        }
    }

    private static boolean isWhole(BigDecimal v) {
        return v.stripTrailingZeros().scale() <= 0;
    }

    private static Set<String> upperIds(List<MatchItem> items) {
        return items.stream().map(i -> i.id().trim().toUpperCase()).collect(Collectors.toSet());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
    }
}
