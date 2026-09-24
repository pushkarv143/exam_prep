package com.examprep.catalog.dto;

import com.examprep.catalog.dto.CatalogDtos.ExamDto;

import java.util.List;
import java.util.UUID;

/**
 * An exam with its full subject, chapter and topic hierarchy. The public variant
 * (active nodes only) is cached in Redis under {@code cache:catalog-tree::{examCode}}.
 * The frontend uses it for filters, the question editor and the test builder.
 */
public record CatalogTreeDto(ExamDto exam, List<SubjectNode> subjects) {

    public record SubjectNode(UUID id, String code, String name, int displayOrder, boolean active,
                              List<ChapterNode> chapters) {
    }

    public record ChapterNode(UUID id, String name, int displayOrder, boolean active, List<TopicNode> topics) {
    }

    public record TopicNode(UUID id, String name, int displayOrder, boolean active) {
    }
}
