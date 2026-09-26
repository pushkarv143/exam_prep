package com.examprep.question.dto;

import java.util.UUID;

/** A question as pinned by a test: the id plus the version the test was built with. */
public record QuestionPin(UUID questionId, int version) {
}
