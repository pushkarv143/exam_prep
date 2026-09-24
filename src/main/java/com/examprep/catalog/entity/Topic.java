package com.examprep.catalog.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Leaf of the catalog. Questions are tagged to a topic, and topic-wise analytics roll up from here. */
@Getter
@Setter
@Entity
@Table(name = "topics")
public class Topic extends BaseEntity {

    @Column(name = "chapter_id", nullable = false, updatable = false)
    private UUID chapterId;

    @Column(nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;
}
