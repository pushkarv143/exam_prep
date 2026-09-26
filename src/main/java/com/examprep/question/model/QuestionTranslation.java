package com.examprep.question.model;

import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The text of a question in a second language. Ids, images and the answer key are shared
 * with the primary language, so a translation carries only strings: option and match
 * texts are keyed by the primary item's id.
 */
public record QuestionTranslation(
        @Size(max = 20_000) String text,
        @Size(max = 20_000) String paragraph,
        @Size(max = 6) Map<String, @Size(max = 5_000) String> options,
        @Size(max = 10) Map<String, @Size(max = 2_000) String> matchLeft,
        @Size(max = 10) Map<String, @Size(max = 2_000) String> matchRight,
        @Size(max = 20_000) String solution) {

    public QuestionTranslation {
        text = blankToNull(text);
        paragraph = blankToNull(paragraph);
        solution = blankToNull(solution);
        options = clean(options);
        matchLeft = clean(matchLeft);
        matchRight = clean(matchRight);
    }

    public boolean hasNoText() {
        return text == null && paragraph == null && solution == null && options.isEmpty() && matchLeft.isEmpty()
                && matchRight.isEmpty();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Drops blank entries and upper-cases ids, keeping a stable (sorted) order. */
    private static Map<String, String> clean(Map<String, String> m) {
        if (m == null || m.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        m.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && !e.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.put(e.getKey().trim().toUpperCase(), e.getValue()));
        return java.util.Collections.unmodifiableMap(out);
    }
}
