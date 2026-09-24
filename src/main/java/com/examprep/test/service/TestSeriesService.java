package com.examprep.test.service;

import com.examprep.catalog.dto.CatalogDtos.ExamDto;
import com.examprep.catalog.service.CatalogQueryService;
import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.enrollment.dto.EnrollmentDto;
import com.examprep.enrollment.service.EnrollmentService;
import com.examprep.security.AuthUser;
import com.examprep.test.dto.SeriesDtos.PublicSeriesDetailDto;
import com.examprep.test.dto.SeriesDtos.PublicSeriesDto;
import com.examprep.test.dto.SeriesDtos.SeriesDto;
import com.examprep.test.dto.SeriesDtos.SeriesRequest;
import com.examprep.test.dto.TestDtos.PublicTestDto;
import com.examprep.test.entity.SeriesStatus;
import com.examprep.test.entity.Test;
import com.examprep.test.entity.TestAvailability;
import com.examprep.test.entity.TestSeries;
import com.examprep.test.entity.TestStatus;
import com.examprep.test.mapper.TestMapper;
import com.examprep.test.repository.TestRepository;
import com.examprep.test.repository.TestSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TestSeriesService {

    static final Set<TestStatus> STUDENT_VISIBLE = EnumSet.of(TestStatus.PUBLISHED, TestStatus.LIVE,
            TestStatus.COMPLETED);

    private final TestSeriesRepository seriesRepository;
    private final TestRepository testRepository;
    private final EnrollmentService enrollmentService;
    private final SeriesAccessService access;
    private final CatalogQueryService catalog;
    private final TestMapper mapper;
    private final Clock clock;

    // ------------------------------------------------------------------ admin

    @Transactional
    public SeriesDto create(SeriesRequest req) {
        validate(req);
        TestSeries series = new TestSeries();
        series.setSlug(uniqueSlug(req.name()));
        apply(series, req);
        seriesRepository.save(series);
        return toAdminDto(series);
    }

    /** The slug stays stable across renames, so shared links and SEO keep working. */
    @Transactional
    public SeriesDto update(UUID id, SeriesRequest req) {
        TestSeries series = load(id);
        validate(req);
        if (!series.getExamId().equals(req.examId()) && testRepository.countBySeriesIdAndStatusIn(id,
                EnumSet.allOf(TestStatus.class)) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Cannot change the exam of a series that has tests");
        }
        apply(series, req);
        return toAdminDto(series);
    }

    @Transactional
    public SeriesDto changeStatus(UUID id, SeriesStatus status) {
        TestSeries series = load(id);
        if (status == SeriesStatus.DRAFT && enrollmentService.countForSeries(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Series has enrollments; archive it instead of moving it back to draft");
        }
        series.setStatus(status);
        return toAdminDto(series);
    }

    @Transactional(readOnly = true)
    public SeriesDto get(UUID id) {
        return toAdminDto(load(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SeriesDto> search(UUID examId, SeriesStatus status, Boolean free, String q,
                                          Pageable pageable) {
        return PageResponse.of(seriesRepository.search(examId, status, free, blankToNull(q), pageable),
                this::toAdminDto);
    }

    // ------------------------------------------------------------------ public storefront

    @Transactional(readOnly = true)
    public PageResponse<PublicSeriesDto> listPublic(String examCode, Boolean free, String q, Pageable pageable,
                                                    AuthUser user) {
        UUID examId = null;
        if (examCode != null && !examCode.isBlank()) {
            examId = catalog.listActiveExams().exams().stream()
                    .filter(e -> e.code().equalsIgnoreCase(examCode.trim()))
                    .map(ExamDto::id).findFirst()
                    .orElseThrow(() -> NotFoundException.of("Exam", examCode));
        }
        Page<TestSeries> page = seriesRepository.searchPublic(examId, free, blankToNull(q), pageable);
        Map<UUID, EnrollmentDto> enrollments = user == null ? Map.of()
                : enrollmentService.findForSeries(user.id(), page.map(TestSeries::getId).getContent());
        return PageResponse.of(page, s -> toPublicDto(s, user, enrollments.get(s.getId())));
    }

    @Transactional(readOnly = true)
    public PublicSeriesDetailDto getPublic(String slug, AuthUser user) {
        TestSeries series = seriesRepository.findBySlug(slug)
                .filter(s -> access.isVisibleTo(user, s))
                .orElseThrow(() -> NotFoundException.of("Test series", slug));
        EnrollmentDto enrollment = user == null ? null : enrollmentService.find(user.id(), series.getId())
                .orElse(null);
        PublicSeriesDto dto = toPublicDto(series, user, enrollment);
        boolean seriesAccess = dto.myAccess() != null && dto.myAccess().hasAccess();

        Instant now = Instant.now(clock);
        List<PublicTestDto> tests = testRepository
                .findBySeriesIdAndStatusInOrderByDisplayOrderAscCreatedAtAsc(series.getId(), STUDENT_VISIBLE).stream()
                .map(t -> toPublicTestDto(t, now, user == null ? null : (seriesAccess || t.isFree())))
                .toList();
        return new PublicSeriesDetailDto(dto, tests);
    }

    /** The caller's enrolled series (newest enrollment first), with access state. */
    @Transactional(readOnly = true)
    public List<PublicSeriesDto> mySeries(AuthUser user) {
        List<EnrollmentDto> enrollments = enrollmentService.listForUser(user.id());
        Map<UUID, TestSeries> byId = seriesRepository.findAllById(enrollments.stream().map(EnrollmentDto::seriesId)
                .toList()).stream().collect(java.util.stream.Collectors.toMap(TestSeries::getId, s -> s));
        return enrollments.stream()
                .filter(e -> byId.containsKey(e.seriesId()))
                .map(e -> toPublicDto(byId.get(e.seriesId()), user, e))
                .toList();
    }

    // ------------------------------------------------------------------ mapping helpers

    PublicSeriesDto toPublicDto(TestSeries s, AuthUser user, EnrollmentDto enrollment) {
        long testCount = testRepository.countBySeriesIdAndStatusIn(s.getId(), STUDENT_VISIBLE);
        return new PublicSeriesDto(s.getId(), s.getExamId(), s.getName(), s.getSlug(), s.getDescription(),
                s.getThumbnailUrl(), s.getPrice(), s.getCurrency(), s.isFree(), s.getValidityDays(),
                s.getValidUntil(), s.isBatchRestricted(), testCount, access.myAccess(user, s, enrollment));
    }

    static PublicTestDto toPublicTestDto(Test t, Instant now, Boolean accessible) {
        return new PublicTestDto(t.getId(), t.getTitle(), t.getDescription(), t.getPattern(), t.getDurationMinutes(),
                t.getTotalMarks(), t.getTotalQuestions(), t.getStartAt(), t.getEndAt(), TestAvailability.of(t, now),
                t.isFree(), t.getDisplayOrder(), accessible);
    }

    private SeriesDto toAdminDto(TestSeries s) {
        return mapper.toDto(s, testRepository.countBySeriesIdAndStatusIn(s.getId(), EnumSet.allOf(TestStatus.class)),
                enrollmentService.countForSeries(s.getId()));
    }

    private void apply(TestSeries s, SeriesRequest req) {
        s.setExamId(req.examId());
        s.setName(req.name().trim());
        s.setDescription(req.description());
        s.setThumbnailUrl(req.thumbnailUrl());
        s.setPrice(req.free() ? java.math.BigDecimal.ZERO : req.price());
        s.setFree(req.free());
        s.setValidityDays(req.validityDays());
        s.setValidUntil(req.validUntil());
        s.setBatchRestricted(req.batchRestricted());
        s.getBatchIds().clear();
        if (req.batchIds() != null) {
            s.getBatchIds().addAll(req.batchIds());
        }
    }

    private static void validate(SeriesRequest req) {
        if (!req.free() && req.price().signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A paid series needs a price greater than 0");
        }
        if (req.batchRestricted() && (req.batchIds() == null || req.batchIds().isEmpty())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A batch-restricted series needs at least one batch");
        }
    }

    private TestSeries load(UUID id) {
        return seriesRepository.findById(id).orElseThrow(() -> NotFoundException.of("Test series", id));
    }

    /** "JEE Main Mock Series 2027!" becomes "jee-main-mock-series-2027", with -2, -3... on collision. */
    String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isEmpty()) {
            base = "series";
        }
        base = base.length() > 200 ? base.substring(0, 200) : base;
        String slug = base;
        for (int i = 2; seriesRepository.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
