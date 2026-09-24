package com.examprep.batch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Join row (batch_id, user_id). It is written with native {@code INSERT ... ON CONFLICT DO NOTHING}
 * (see the repository), so adding a member is idempotent and needs no read first.
 */
@Getter
@Entity
@Table(name = "batch_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchMember {

    @EmbeddedId
    private Id id;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Getter
    @Embeddable
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Id implements Serializable {

        @Column(name = "batch_id")
        private UUID batchId;

        @Column(name = "user_id")
        private UUID userId;
    }
}
