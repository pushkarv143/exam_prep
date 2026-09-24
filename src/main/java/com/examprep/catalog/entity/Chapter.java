package com.examprep.catalog.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "chapters")
public class Chapter extends BaseEntity {

    /** Immutable: questions store a denormalised subject id, so chapters cannot move. */
    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;
}
