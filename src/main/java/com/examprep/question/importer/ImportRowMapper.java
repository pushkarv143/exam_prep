package com.examprep.question.importer;

import com.examprep.question.dto.QuestionRequest;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent;
import com.examprep.question.model.QuestionContent.Media;
import com.examprep.question.model.QuestionContent.Option;
import com.examprep.question.model.QuestionContent.Solution;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.examprep.question.importer.ImportColumns.*;

/**
 * Converts one sheet row into a {@link QuestionRequest}. It only parses formats; the
 * domain rules are applied later by {@code QuestionValidator}, exactly as for API
 * requests. Parse problems throw {@link IllegalArgumentException} with a message that
 * is shown to the uploader next to the row number.
 *
 * <p>Import supports SINGLE_CORRECT, MULTIPLE_CORRECT and NUMERICAL. MATCH and
 * PARAGRAPH need nested structures that do not fit a flat sheet; create them in the
 * question editor.
 */
public final class ImportRowMapper {

    /** Friendly aliases teachers commonly type. */
    private static final Map<String, QuestionType> TYPE_ALIASES = Map.of(
            "SCQ", QuestionType.SINGLE_CORRECT, "SINGLE", QuestionType.SINGLE_CORRECT,
            "MCQ", QuestionType.MULTIPLE_CORRECT, "MULTIPLE", QuestionType.MULTIPLE_CORRECT,
            "INTEGER", QuestionType.NUMERICAL, "NUMERIC", QuestionType.NUMERICAL);

    private ImportRowMapper() {
    }

    public static QuestionRequest toRequest(ImportRow row, UUID topicId) {
        QuestionType type = parseType(row.required(TYPE));

        List<Option> options = new ArrayList<>();
        for (String letter : OPTION_LETTERS) {
            String text = row.get(OPTION_PREFIX + letter.toLowerCase(Locale.ROOT));
            if (text != null) {
                options.add(new Option(letter, text, null));
            }
        }

        String correct = row.required(CORRECT_ANSWER);
        AnswerKey key = switch (type) {
            case SINGLE_CORRECT, MULTIPLE_CORRECT -> new AnswerKey(
                    Arrays.stream(correct.split("[,;\\s]+")).filter(s -> !s.isBlank()).toList(), null, null, null);
            case NUMERICAL -> new AnswerKey(null, decimal(correct, CORRECT_ANSWER), optionalDecimal(row, TOLERANCE),
                    null);
            default -> throw new IllegalArgumentException(type + " cannot be bulk-imported; use the question editor");
        };

        String solutionText = row.get(SOLUTION_TEXT);
        String videoUrl = row.get(SOLUTION_VIDEO_URL);
        Solution solution = solutionText == null && videoUrl == null ? null
                : new Solution(solutionText, videoUrl, List.of());
        String imageUrl = row.get(IMAGE_URL);
        List<Media> images = imageUrl == null ? List.of() : List.of(new Media(imageUrl, null));

        QuestionContent content = new QuestionContent(row.required(QUESTION_TEXT), images, options, null,
                null, null, solution);

        return new QuestionRequest(
                type,
                optionalEnum(row, DIFFICULTY, Difficulty.class),
                optionalEnum(row, LANGUAGE, Language.class),
                topicId,
                null,
                content,
                key,
                optionalDecimal(row, MARKS),
                optionalDecimal(row, NEGATIVE_MARKS),
                QuestionStatus.ACTIVE,
                row.get(SOURCE),
                optionalInt(row, YEAR),
                parseTags(row.get(TAGS)));
    }

    static QuestionType parseType(String raw) {
        String v = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        QuestionType alias = TYPE_ALIASES.get(v);
        if (alias != null) {
            return alias;
        }
        try {
            return QuestionType.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown type '" + raw + "' (use SINGLE_CORRECT, MULTIPLE_CORRECT "
                    + "or NUMERICAL)");
        }
    }

    private static <E extends Enum<E>> E optionalEnum(ImportRow row, String column, Class<E> type) {
        String v = row.get(column);
        if (v == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + column + " '" + v + "' (allowed: "
                    + Arrays.toString(type.getEnumConstants()) + ")");
        }
    }

    private static BigDecimal optionalDecimal(ImportRow row, String column) {
        String v = row.get(column);
        return v == null ? null : decimal(v, column);
    }

    private static BigDecimal decimal(String v, String column) {
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + column + "' must be a number, got '" + v + "'");
        }
    }

    private static Integer optionalInt(ImportRow row, String column) {
        String v = row.get(column);
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + column + "' must be a whole number, got '" + v + "'");
        }
    }

    private static Set<String> parseTags(String raw) {
        if (raw == null) {
            return Set.of();
        }
        return Arrays.stream(raw.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
