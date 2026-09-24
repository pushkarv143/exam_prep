package com.examprep.test.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A sellable bundle of tests. It is the unit of enrollment and payment. */
@Getter
@Setter
@Entity
@Table(name = "test_series")
public class TestSeries extends BaseEntity {

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "is_free", nullable = false)
    private boolean free;

    /** Access length from enrollment; null = until {@link #validUntil} (or forever). */
    @Column(name = "validity_days")
    private Integer validityDays;

    @Column(name = "valid_until")
    private Instant validUntil;

    /** Only members of {@link #batchIds} may enroll or buy. */
    @Column(name = "batch_restricted", nullable = false)
    private boolean batchRestricted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeriesStatus status = SeriesStatus.DRAFT;

    /** Members of these batches get the series without payment. */
    @ElementCollection
    @CollectionTable(name = "test_series_batches", joinColumns = @JoinColumn(name = "series_id"))
    @Column(name = "batch_id", nullable = false)
    private Set<UUID> batchIds = new HashSet<>();

    /** Earliest of (now + validityDays) and validUntil; null if neither is set. */
    public Instant accessExpiry(Instant now) {
        Instant byDays = validityDays == null ? null : now.plusSeconds(validityDays * 86_400L);
        if (byDays == null) {
            return validUntil;
        }
        return validUntil == null || byDays.isBefore(validUntil) ? byDays : validUntil;
    }
}
