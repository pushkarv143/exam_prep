package com.examprep.test.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.common.api.PageResponse;
import com.examprep.enrollment.dto.EnrollmentDto;
import com.examprep.enrollment.service.EnrollmentService;
import com.examprep.test.dto.SeriesDtos.AdminGrantRequest;
import com.examprep.test.dto.SeriesDtos.SeriesDto;
import com.examprep.test.dto.SeriesDtos.SeriesRequest;
import com.examprep.test.entity.SeriesStatus;
import com.examprep.test.service.SeriesAccessService;
import com.examprep.test.service.TestSeriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Series are commercial products (price, access), so writes are ADMIN-only. Teachers can read. */
@Tag(name = "Test series (admin)")
@RestController
@RequestMapping("/api/v1/admin/series")
@RequiredArgsConstructor
@PreAuthorize("@perm.has('admin.access')")
public class AdminSeriesController {

    private final TestSeriesService seriesService;
    private final SeriesAccessService accessService;
    private final EnrollmentService enrollmentService;

    @PreAuthorize("@perm.has('series.view')")
    @GetMapping
    public ApiResponse<PageResponse<SeriesDto>> search(
            @RequestParam(required = false) UUID examId, @RequestParam(required = false) SeriesStatus status,
            @RequestParam(required = false) Boolean free, @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(seriesService.search(examId, status, free, q, pageable));
    }

    @PreAuthorize("@perm.has('series.view')")
    @GetMapping("/{id}")
    public ApiResponse<SeriesDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(seriesService.get(id));
    }

    @PreAuthorize("@perm.has('series.manage')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SeriesDto> create(@Valid @RequestBody SeriesRequest request) {
        return ApiResponse.ok(seriesService.create(request));
    }

    @PreAuthorize("@perm.has('series.manage')")
    @PutMapping("/{id}")
    public ApiResponse<SeriesDto> update(@PathVariable UUID id, @Valid @RequestBody SeriesRequest request) {
        return ApiResponse.ok(seriesService.update(id, request));
    }

    @Operation(summary = "Publish (list publicly, enable enrollment/purchase)")
    @PreAuthorize("@perm.has('series.publish')")
    @PostMapping("/{id}/publish")
    public ApiResponse<SeriesDto> publish(@PathVariable UUID id) {
        return ApiResponse.ok(seriesService.changeStatus(id, SeriesStatus.PUBLISHED));
    }

    @Operation(summary = "Archive (stop selling; existing enrollments keep access)")
    @PreAuthorize("@perm.has('series.publish')")
    @PostMapping("/{id}/archive")
    public ApiResponse<SeriesDto> archive(@PathVariable UUID id) {
        return ApiResponse.ok(seriesService.changeStatus(id, SeriesStatus.ARCHIVED));
    }

    @Operation(summary = "Back to draft (only if nobody is enrolled)")
    @PreAuthorize("@perm.has('series.publish')")
    @PostMapping("/{id}/unpublish")
    public ApiResponse<SeriesDto> unpublish(@PathVariable UUID id) {
        return ApiResponse.ok(seriesService.changeStatus(id, SeriesStatus.DRAFT));
    }

    @PreAuthorize("@perm.has('series.view')")
    @GetMapping("/{id}/enrollments")
    public ApiResponse<PageResponse<EnrollmentDto>> enrollments(
            @PathVariable UUID id,
            @ParameterObject @PageableDefault(size = 50, sort = "enrolledAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(enrollmentService.listForSeries(id, pageable));
    }

    @Operation(summary = "Grant a student access without payment (support, scholarships)")
    @PreAuthorize("@perm.has('series.manage')")
    @PostMapping("/{id}/enrollments")
    public ApiResponse<EnrollmentDto> grant(@PathVariable UUID id, @Valid @RequestBody AdminGrantRequest request) {
        return ApiResponse.ok(accessService.grantByAdmin(request.userId(), id));
    }

    @Operation(summary = "Revoke a student's access (e.g. after a refund)")
    @PreAuthorize("@perm.has('series.manage')")
    @DeleteMapping("/{id}/enrollments/{userId}")
    public ApiResponse<Void> revoke(@PathVariable UUID id, @PathVariable UUID userId) {
        enrollmentService.cancel(userId, id);
        return ApiResponse.ok();
    }
}
