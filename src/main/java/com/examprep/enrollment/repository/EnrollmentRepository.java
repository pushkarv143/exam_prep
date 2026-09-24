package com.examprep.enrollment.repository;

import com.examprep.enrollment.entity.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByUserIdAndSeriesId(UUID userId, UUID seriesId);

    List<Enrollment> findByUserIdOrderByEnrolledAtDesc(UUID userId);

    List<Enrollment> findByUserIdAndSeriesIdIn(UUID userId, Collection<UUID> seriesIds);

    Page<Enrollment> findBySeriesId(UUID seriesId, Pageable pageable);

    long countBySeriesId(UUID seriesId);

    /**
     * Atomic grant-or-renew.
     * <ul>
     *   <li>No row: insert an ACTIVE enrollment.</li>
     *   <li>Existing row: reactivate it, and keep the <em>later</em> expiry (NULL means
     *       "forever" and wins), so an admin grant never shortens a paid enrollment.</li>
     * </ul>
     * A single statement, so concurrent grants (double click, verify racing the webhook)
     * cannot violate {@code ux_enrollments_user_series}.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO enrollments (id, user_id, series_id, payment_id, source, status, enrolled_at, expires_at,
                                     version, created_at, updated_at, created_by, updated_by)
            VALUES (:id, :userId, :seriesId, :paymentId, :source, 'ACTIVE', :now, :expiresAt,
                    0, :now, :now, :actor, :actor)
            ON CONFLICT (user_id, series_id) DO UPDATE SET
                status      = 'ACTIVE',
                source      = EXCLUDED.source,
                payment_id  = COALESCE(EXCLUDED.payment_id, enrollments.payment_id),
                enrolled_at = CASE WHEN enrollments.status = 'ACTIVE'
                                        AND (enrollments.expires_at IS NULL OR enrollments.expires_at > :now)
                                   THEN enrollments.enrolled_at ELSE EXCLUDED.enrolled_at END,
                expires_at  = CASE WHEN enrollments.status <> 'ACTIVE' THEN EXCLUDED.expires_at
                                   WHEN enrollments.expires_at IS NULL OR EXCLUDED.expires_at IS NULL THEN NULL
                                   ELSE GREATEST(enrollments.expires_at, EXCLUDED.expires_at) END,
                version     = enrollments.version + 1,
                updated_at  = :now,
                updated_by  = :actor
            """, nativeQuery = true)
    void upsertActive(@Param("id") UUID id, @Param("userId") UUID userId, @Param("seriesId") UUID seriesId,
                      @Param("paymentId") UUID paymentId, @Param("source") String source,
                      @Param("now") Instant now, @Param("expiresAt") Instant expiresAt,
                      @Param("actor") UUID actor);

    @Query("""
            select count(e) > 0 from Enrollment e
            where e.userId = :userId and e.seriesId = :seriesId
              and e.status = com.examprep.enrollment.entity.EnrollmentStatus.ACTIVE
              and (e.expiresAt is null or e.expiresAt > :now)
            """)
    boolean isActive(@Param("userId") UUID userId, @Param("seriesId") UUID seriesId, @Param("now") Instant now);
}
