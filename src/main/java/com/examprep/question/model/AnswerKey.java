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
 * MULTIPLE_CORRECT {"options": ["A", "C"], "partial": "JEE_ADVANCED"}
 * NUMERICAL        {"value": 2.5, "tolerance": 0.01}   or a range   {"min": 2.4, "max": 2.6}
 * MATCH            {"pairs": {"P": "2", "Q": "1"}}
 * PARAGRAPH        {}
 * </pre>
 *
 * <p>The compact constructor <b>canonicalises</b> the key: option ids are upper-cased,
 * de-duplicated and sorted, decimals have trailing zeros stripped, and pairs are sorted.
 * Equal answers therefore compare equal. The evaluator relies on this, and so does the
 * "scoring changed?" check that decides whether a new question version may reach a
 * published test.
 *
 * @param partial MULTIPLE_CORRECT only: the partial-marking rule (null = JEE Advanced)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerKey(
        @Size(max = 6) List<String> options,
        BigDecimal value,
        BigDecimal tolerance,
        @Size(max = 10) Map<String, String> pairs,
        BigDecimal min,
        BigDecimal max,
        PartialRule partial) {

    public static final AnswerKey EMPTY = new AnswerKey(null, null, null, null);

    public AnswerKey {
        options = options == null ? List.of() : options.stream()
                .filter(o -> o != null && !o.isBlank())
                .map(o -> o.trim().toUpperCase())
                .distinct().sorted().toList();
        value = strip(value);
        tolerance = tolerance == null || tolerance.signum() == 0 ? null : tolerance.stripTrailingZeros();
        min = strip(min);
        max = strip(max);
        partial = partial == PartialRule.JEE_ADVANCED ? null : partial;   // the default is not stored
        if (pairs == null) {
            pairs = Map.of();
        } else {
            TreeMap<String, String> sorted = new TreeMap<>();
            pairs.forEach((k, v) -> sorted.put(k.trim().toUpperCase(), v.trim().toUpperCase()));
            pairs = java.util.Collections.unmodifiableMap(sorted);
        }
    }

    /** Key without the A2 fields (range, partial rule). */
    public AnswerKey(List<String> options, BigDecimal value, BigDecimal tolerance, Map<String, String> pairs) {
        this(options, value, tolerance, pairs, null, null, null);
    }

    /** True for a NUMERICAL key given as a range {@code [min, max]}. */
    public boolean hasRange() {
        return min != null || max != null;
    }

    /** The partial-marking rule, with the default applied. */
    public PartialRule partialRule() {
        return partial == null ? PartialRule.JEE_ADVANCED : partial;
    }

    private static BigDecimal strip(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros();
    }
}
