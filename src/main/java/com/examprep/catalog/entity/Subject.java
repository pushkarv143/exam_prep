package com.examprep.catalog.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Catalog hierarchy links are plain FK columns rather than {@code @ManyToOne}. The tree
 * is always assembled with one query per level (no N+1 and no lazy-loading surprises),
 * and other modules can hold catalog ids without depending on these entities.
 */
@Getter
@Setter
@Entity
@Table(name = "subjects")
public class Subject extends BaseEntity {

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;
}
