package com.examprep.catalog.dto;

import java.util.UUID;

/**
 * A topic together with all its ancestors. It is the catalog module's public contract
 * for other modules: the question bank stores these ids denormalised, and
 * analytics/UI show the names.
 */
public record TopicPath(
        UUID examId, String examCode,
        UUID subjectId, String subjectName,
        UUID chapterId, String chapterName,
        UUID topicId, String topicName) {
}
