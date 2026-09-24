package com.examprep.batch.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A cohort of students, e.g. a classroom batch. A test series can be linked to batches:
 * members get it without paying, and a batch-restricted series is sold/visible only to members.
 */
@Getter
@Setter
@Entity
@Table(name = "batches")
public class Batch extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "exam_id")
    private UUID examId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active = true;
}
