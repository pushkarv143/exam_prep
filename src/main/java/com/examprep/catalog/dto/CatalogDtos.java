package com.examprep.catalog.dto;

import java.util.UUID;

/** Flat catalog DTOs (one per level), grouped in one file because they are tiny and change together. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record ExamDto(UUID id, String code, String name, String description, boolean active, int displayOrder) {
    }

    public record SubjectDto(UUID id, UUID examId, String code, String name, int displayOrder, boolean active) {
    }

    public record ChapterDto(UUID id, UUID subjectId, String name, int displayOrder, boolean active) {
    }

    public record TopicDto(UUID id, UUID chapterId, String name, int displayOrder, boolean active) {
    }
}
