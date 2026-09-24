package com.examprep.test.entity;

public enum SeriesStatus {
    DRAFT,
    /** Listed publicly and purchasable/enrollable. */
    PUBLISHED,
    /** No longer sold or listed. Existing enrollments keep their access. */
    ARCHIVED
}
