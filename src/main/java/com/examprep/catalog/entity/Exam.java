package com.examprep.catalog.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Top of the catalog, e.g. JEE_MAIN, JEE_ADVANCED, NEET. */
@Getter
@Setter
@Entity
@Table(name = "exams")
public class Exam extends BaseEntity {

    /** Stable, upper-case business key used in URLs and imports. */
    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
