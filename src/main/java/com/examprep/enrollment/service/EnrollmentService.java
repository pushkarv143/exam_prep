package com.examprep.enrollment.service;

import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.NotFoundException;
import com.examprep.common.util.Uuids;
import com.examprep.enrollment.dto.EnrollmentDto;
import com.examprep.enrollment.entity.Enrollment;
import com.examprep.enrollment.entity.EnrollmentSource;
import com.examprep.enrollment.entity.EnrollmentStatus;
import com.examprep.enrollment.repository.EnrollmentRepository;
import com.examprep.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Persistence of "who has access to which series". The <em>business rules</em>
 * (free / paid / batch, validity) live in the test module's {@code SeriesAccessService},
 * which knows about series. This service only records and answers.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository repository;
    private final EntityManager entityManager;
    private final Clock clock;

    /** Grants or renews access (idempotent). {@code expiresAt == null} means no expiry. */
    @Transactional
    public EnrollmentDto grant(UUID userId, UUID seriesId, EnrollmentSource source, UUID paymentId,
                               Instant expiresAt) {
        UUID actor = SecurityUtils.currentUserId().orElse(userId);
        repository.upsertActive(Uuids.v7(), userId, seriesId, paymentId, source.name(), Instant.now(clock),
                expiresAt, actor);
        // The native upsert bypasses the persistence context. Refresh, don't clear: clearing
        // would silently detach the caller's pending changes (e.g. the Payment being marked PAID).
        Enrollment enrollment = repository.findByUserIdAndSeriesId(userId, seriesId).orElseThrow();
        entityManager.refresh(enrollment);
        return toDto(enrollment);
    }

    @Transactional
    public void cancel(UUID userId, UUID seriesId) {
        Enrollment e = repository.findByUserIdAndSeriesId(userId, seriesId)
                .orElseThrow(() -> new NotFoundException("No enrollment for user " + userId));
        e.setStatus(EnrollmentStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID userId, UUID seriesId) {
        return repository.isActive(userId, seriesId, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public Optional<EnrollmentDto> find(UUID userId, UUID seriesId) {
        return repository.findByUserIdAndSeriesId(userId, seriesId).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Map<UUID, EnrollmentDto> findForSeries(UUID userId, Collection<UUID> seriesIds) {
        if (seriesIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByUserIdAndSeriesIdIn(userId, seriesIds).stream()
                .map(this::toDto).collect(Collectors.toMap(EnrollmentDto::seriesId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDto> listForUser(UUID userId) {
        return repository.findByUserIdOrderByEnrolledAtDesc(userId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentDto> listForSeries(UUID seriesId, Pageable pageable) {
        return PageResponse.of(repository.findBySeriesId(seriesId, pageable), this::toDto);
    }

    @Transactional(readOnly = true)
    public long countForSeries(UUID seriesId) {
        return repository.countBySeriesId(seriesId);
    }

    private EnrollmentDto toDto(Enrollment e) {
        return new EnrollmentDto(e.getId(), e.getUserId(), e.getSeriesId(), e.getSource(), e.getStatus(),
                e.getEnrolledAt(), e.getExpiresAt(), e.getPaymentId(), e.isActiveAt(Instant.now(clock)));
    }
}
