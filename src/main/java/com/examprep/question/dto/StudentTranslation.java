package com.examprep.question.dto;

import com.examprep.question.model.QuestionTranslation;

import java.util.Map;

/** The student-visible part of a translation (no solution). Option texts are keyed by option id. */
public record StudentTranslation(String text, Map<String, String> options, Map<String, String> matchLeft,
                                 Map<String, String> matchRight) {

    public static StudentTranslation of(QuestionTranslation t) {
        return new StudentTranslation(t.text(), t.options(), t.matchLeft(), t.matchRight());
    }
}
