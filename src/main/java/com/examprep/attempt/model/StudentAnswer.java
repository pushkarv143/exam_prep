package com.examprep.attempt.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * A student's response. It mirrors {@code AnswerKey} so evaluation can compare them directly.
 * <pre>
 * SINGLE_CORRECT   {"options": ["B"]}
 * MULTIPLE_CORRECT {"options": ["A", "C"]}
 * NUMERICAL        {"value": "2.50"}         (kept as typed; parsed during evaluation)
 * MATCH            {"pairs": {"P": "2"}}
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentAnswer(
        @Size(max = 6) List<@Size(max = 5) String> options,
        @Size(max = 24) String value,
        @Size(max = 10) Map<String, String> pairs) {

    @JsonIgnore   // otherwise Jackson persists it as an "empty" property
    public boolean isEmpty() {
        return (options == null || options.isEmpty()) && (value == null || value.isBlank())
                && (pairs == null || pairs.isEmpty());
    }
}
