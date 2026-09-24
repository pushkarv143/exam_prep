package com.examprep.question.service;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent;
import com.examprep.question.model.QuestionContent.MatchItem;
import com.examprep.question.model.QuestionContent.Option;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cross-field rules that Bean Validation cannot express: whether the content and the
 * answer key agree for the given type. A question that passes here can always be
 * rendered and evaluated.
 */
@Component
public class QuestionValidator {

    static final int MIN_OPTIONS = 2;

    public void validate(QuestionType type, QuestionContent content, AnswerKey key) {
        if (type == QuestionType.PARAGRAPH) {
            require(notBlank(content.paragraph()), "PARAGRAPH requires 'content.paragraph'");
            return;
        }
        require(notBlank(content.text()), "'content.text' is required");
        require(key != null, "'answerKey' is required for " + type);

        switch (type) {
            case SINGLE_CORRECT, MULTIPLE_CORRECT -> validateChoice(type, content, key);
            case NUMERICAL -> validateNumerical(content, key);
            case MATCH -> validateMatch(content, key);
            default -> throw new IllegalStateException("Unhandled type " + type);
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
        require(key.value() != null, "NUMERICAL answer key needs 'value'");
        require(key.tolerance() == null || key.tolerance().signum() > 0, "'tolerance' must be positive");
        require(key.options().isEmpty() && key.pairs().isEmpty(), "NUMERICAL uses only 'value' and 'tolerance'");
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
