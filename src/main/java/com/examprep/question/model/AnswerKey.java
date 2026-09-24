package com.examprep.question.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Typed shape of {@code questions.correct_answer} (JSONB). <b>Never sent to students
 * during a test.</b> Which fields are used depends on the question type:
 * <pre>
 * SINGLE_CORRECT   {"options": ["B"]}
 * MULTIPLE_CORRECT {"options": ["A", "C"]}
 * NUMERICAL        {"value": 2.5, "tolerance": 0.01}
 * MATCH            {"pairs": {"P": "2", "Q": "1"}}
 * PARAGRAPH        {}
 * </pre>
 *
 * <p>The compact constructor <b>canonicalises</b> the key: option ids are upper-cased,
 * de-duplicated and sorted, decimals have trailing zeros stripped, and pairs are sorted.
 * Equal answers therefore compare equal. The evaluator relies on this, and so does the
 * "answer key changed?" check that protects published tests.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerKey(
        @Size(max = 6) List<String> options,
        BigDecimal value,
        BigDecimal tolerance,
        @Size(max = 10) Map<String, String> pairs) {

    public static final AnswerKey EMPTY = new AnswerKey(null, null, null, null);

    public AnswerKey {
        options = options == null ? List.of() : options.stream()
                .filter(o -> o != null && !o.isBlank())
                .map(o -> o.trim().toUpperCase())
                .distinct().sorted().toList();
        value = value == null ? null : value.stripTrailingZeros();
        tolerance = tolerance == null || tolerance.signum() == 0 ? null : tolerance.stripTrailingZeros();
        if (pairs == null) {
            pairs = Map.of();
        } else {
            TreeMap<String, String> sorted = new TreeMap<>();
            pairs.forEach((k, v) -> sorted.put(k.trim().toUpperCase(), v.trim().toUpperCase()));
            pairs = java.util.Collections.unmodifiableMap(sorted);
        }
    }
}
