package com.examprep.question.event;

import java.util.UUID;

/** A question's student-visible content was edited. Cached test papers that contain it must be rebuilt. */
public record QuestionContentChangedEvent(UUID questionId) {
}
