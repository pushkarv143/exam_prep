package com.examprep.attempt.service;

import com.examprep.attempt.model.StudentAnswer;
import com.examprep.attempt.paper.PaperIndex;
import com.examprep.question.entity.QuestionType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Normalises and validates a student's answer against the question's type and options.
 * It is pure and in-memory: autosave is on the hot path.
 *
 * <p>Normalisation: option ids are upper-cased, de-duplicated and sorted; numerical values
 * are trimmed; an empty answer becomes {@code null} ("clear response"). Invalid input
 * throws {@link InvalidAnswerException} with a user-safe reason.
 */
@Component
public class AnswerValidator {

    /** Up to 10 integer digits and 6 decimals, optional sign (what an NTA numeric keypad can produce). */
    private static final Pattern NUMERIC = Pattern.compile("^-?\\d{1,10}(\\.\\d{1,6})?$");

    public StudentAnswer normalize(PaperIndex.Entry question, StudentAnswer raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return switch (question.type()) {
            case SINGLE_CORRECT, MULTIPLE_CORRECT -> choice(question, raw);
            case NUMERICAL -> numerical(raw);
            case MATCH -> match(question, raw);
            case PARAGRAPH -> throw new InvalidAnswerException("Paragraphs cannot be answered");
        };
    }

    private static StudentAnswer choice(PaperIndex.Entry q, StudentAnswer raw) {
        if (raw.value() != null || (raw.pairs() != null && !raw.pairs().isEmpty())) {
            throw new InvalidAnswerException("Choose from the options");
        }
        List<String> ids = raw.options().stream().map(o -> o.trim().toUpperCase(Locale.ROOT))
                .distinct().sorted().toList();
        if (q.type() == QuestionType.SINGLE_CORRECT && ids.size() > 1) {
            throw new InvalidAnswerException("Only one option can be selected");
        }
        for (String id : ids) {
            if (!q.optionIds().contains(id)) {
                throw new InvalidAnswerException("Unknown option '" + id + "'");
            }
        }
        return new StudentAnswer(ids, null, null);
    }

    private static StudentAnswer numerical(StudentAnswer raw) {
        if ((raw.options() != null && !raw.options().isEmpty()) || (raw.pairs() != null && !raw.pairs().isEmpty())) {
            throw new InvalidAnswerException("Enter a numeric value");
        }
        String value = raw.value().trim();
        if (!NUMERIC.matcher(value).matches()) {
            throw new InvalidAnswerException("Enter a valid number");
        }
        return new StudentAnswer(null, value, null);
    }

    private static StudentAnswer match(PaperIndex.Entry q, StudentAnswer raw) {
        if (raw.pairs() == null || raw.pairs().isEmpty()) {
            throw new InvalidAnswerException("Match the items");
        }
        Map<String, String> pairs = new TreeMap<>();
        raw.pairs().forEach((k, v) -> {
            String left = k.trim().toUpperCase(Locale.ROOT);
            String right = v.trim().toUpperCase(Locale.ROOT);
            if (!q.leftIds().contains(left) || !q.rightIds().contains(right)) {
                throw new InvalidAnswerException("Unknown match item " + left + "-" + right);
            }
            pairs.put(left, right);
        });
        return new StudentAnswer(null, null, pairs);
    }

    public static class InvalidAnswerException extends RuntimeException {
        public InvalidAnswerException(String message) {
            super(message);
        }
    }
}
