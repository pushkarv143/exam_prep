package com.examprep.test.service;

import com.examprep.batch.service.BatchService;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.enrollment.dto.EnrollmentDto;
import com.examprep.enrollment.entity.EnrollmentSource;
import com.examprep.enrollment.service.EnrollmentService;
import com.examprep.security.AuthUser;
import com.examprep.test.dto.SeriesDtos.MyAccess;
import com.examprep.test.entity.SeriesStatus;
import com.examprep.test.entity.Test;
import com.examprep.test.entity.TestSeries;
import com.examprep.test.repository.TestSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * The single source of truth for "who may see / take / buy what".
 *
 * <pre>
 * Test access (student):
 *   staff (ADMIN/TEACHER)                        yes
 *   test not published                           no
 *   standalone test (no series)                  only if test.free
 *   free sample test in a series                 yes (members only if the series is batch-restricted)
 *   series.free                                  yes (members only if batch-restricted)
 *   member of one of the series' batches         yes
 *   active, unexpired enrollment                 yes
 *   otherwise                                    no, enroll or pay
 * </pre>
 * Every check is evaluated live, so batch membership, expiry and cancellation take
 * effect immediately without batch jobs.
 */
@Service
@RequiredArgsConstructor
public class SeriesAccessService {

    private final TestSeriesRepository seriesRepository;
    private final EnrollmentService enrollmentService;
    private final BatchService batchService;
    private final Clock clock;

    // ------------------------------------------------------------------ checks

    @Transactional(readOnly = true)
    public boolean canAccessTest(AuthUser user, Test test) {
        if (user == null) {
            return false;
        }
        if (user.isStaff()) {
            return true;
        }
        if (!test.getStatus().isVisibleToStudents()) {
            return false;
        }
        if (test.getSeriesId() == null) {
            return test.isFree();
        }
        TestSeries series = seriesRepository.findById(test.getSeriesId()).orElse(null);
        if (series == null) {
            return false;
        }
        if (test.isFree()) {
            return series.getStatus() != SeriesStatus.DRAFT && (!series.isBatchRestricted() || isMember(user, series));
        }
        return canAccessSeries(user, series);
    }

    /** Access to the paid (non-sample) tests of a series. */
    @Transactional(readOnly = true)
    public boolean canAccessSeries(AuthUser user, TestSeries series) {
        if (user == null) {
            return false;
        }
        if (user.isStaff()) {
            return true;
        }
        if (series.getStatus() == SeriesStatus.DRAFT) {
            return false;
        }
        boolean member = isMember(user, series);
        if (series.isFree()) {
            return !series.isBatchRestricted() || member;
        }
        return member || enrollmentService.isActive(user.id(), series.getId());
    }

    /** Batch-restricted series are invisible to non-members (the public API returns 404). */
    public boolean isVisibleTo(AuthUser user, TestSeries series) {
        if (user != null && user.isStaff()) {
            return true;
        }
        if (series.getStatus() == SeriesStatus.DRAFT) {
            return false;
        }
        return !series.isBatchRestricted() || (user != null && isMember(user, series));
    }

    public MyAccess myAccess(AuthUser user, TestSeries series, EnrollmentDto enrollment) {
        if (user == null) {
            return null;
        }
        boolean enrolled = enrollment != null && enrollment.active();
        return new MyAccess(enrolled, canAccessSeries(user, series),
                enrolled ? enrollment.source() : null, enrolled ? enrollment.expiresAt() : null);
    }

    // ------------------------------------------------------------------ enrollment flows

    /**
     * Self-enrollment without payment (free series, or a batch member). Idempotent: an
     * active enrollment is returned as-is. A paid series answers 402 PAYMENT_REQUIRED,
     * and the client then starts the checkout.
     */
    @Transactional
    public EnrollmentDto enrollSelf(AuthUser user, UUID seriesId) {
        TestSeries series = seriesRepository.findById(seriesId)
                .filter(s -> s.getStatus() == SeriesStatus.PUBLISHED)
                .orElseThrow(() -> NotFoundException.of("Test series", seriesId));
        boolean member = isMember(user, series);
        if (series.isBatchRestricted() && !member) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This test series is available only to its batches");
        }
        EnrollmentDto existing = enrollmentService.find(user.id(), seriesId).filter(EnrollmentDto::active)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        Instant expiry = series.accessExpiry(Instant.now(clock));
        if (series.isFree()) {
            return enrollmentService.grant(user.id(), seriesId, EnrollmentSource.FREE, null, expiry);
        }
        if (member) {
            return enrollmentService.grant(user.id(), seriesId, EnrollmentSource.BATCH, null, expiry);
        }
        throw new BusinessException(ErrorCode.PAYMENT_REQUIRED);
    }

    /** Checks that a user may buy the series now, and returns it (used by payment order creation). */
    @Transactional(readOnly = true)
    public TestSeries requirePurchasable(AuthUser user, UUID seriesId) {
        TestSeries series = seriesRepository.findById(seriesId)
                .filter(s -> s.getStatus() == SeriesStatus.PUBLISHED)
                .orElseThrow(() -> NotFoundException.of("Test series", seriesId));
        if (series.isFree() || series.getPrice().signum() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "This series is free; enroll directly");
        }
        if (series.isBatchRestricted() && !isMember(user, series)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This test series is available only to its batches");
        }
        if (canAccessSeries(user, series)) {
            throw new BusinessException(ErrorCode.CONFLICT, "You already have access to this test series");
        }
        return series;
    }

    @Transactional
    public EnrollmentDto grantPaid(UUID userId, UUID seriesId, UUID paymentId) {
        TestSeries series = seriesRepository.findById(seriesId).orElseThrow();
        return enrollmentService.grant(userId, seriesId, EnrollmentSource.PAYMENT, paymentId,
                series.accessExpiry(Instant.now(clock)));
    }

    @Transactional
    public EnrollmentDto grantByAdmin(UUID userId, UUID seriesId) {
        TestSeries series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> NotFoundException.of("Test series", seriesId));
        return enrollmentService.grant(userId, seriesId, EnrollmentSource.ADMIN, null,
                series.accessExpiry(Instant.now(clock)));
    }

    private boolean isMember(AuthUser user, TestSeries series) {
        return !series.getBatchIds().isEmpty() && batchService.isMemberOfAnyActive(user.id(), series.getBatchIds());
    }
}
