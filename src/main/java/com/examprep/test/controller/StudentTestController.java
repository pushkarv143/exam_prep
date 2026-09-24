package com.examprep.test.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.enrollment.dto.EnrollmentDto;
import com.examprep.enrollment.service.EnrollmentService;
import com.examprep.security.AuthUser;
import com.examprep.test.dto.TestDtos.TestInfoDto;
import com.examprep.test.service.SeriesAccessService;
import com.examprep.test.dto.SeriesDtos.PublicSeriesDto;
import com.examprep.test.service.StudentTestService;
import com.examprep.test.service.TestSeriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Student: series & tests")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StudentTestController {

    private final SeriesAccessService accessService;
    private final EnrollmentService enrollmentService;
    private final StudentTestService studentTestService;
    private final TestSeriesService seriesService;

    @Operation(summary = "Enroll in a free series (or as a batch member). Paid series return 402.")
    @PostMapping("/series/{seriesId}/enroll")
    public ApiResponse<EnrollmentDto> enroll(@AuthenticationPrincipal AuthUser user, @PathVariable UUID seriesId) {
        return ApiResponse.ok(accessService.enrollSelf(user, seriesId));
    }

    @Operation(summary = "My enrollments")
    @GetMapping("/me/enrollments")
    public ApiResponse<List<EnrollmentDto>> myEnrollments(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(enrollmentService.listForUser(user.id()));
    }

    @Operation(summary = "My enrolled series with details and access state")
    @GetMapping("/me/series")
    public ApiResponse<List<PublicSeriesDto>> mySeries(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(seriesService.mySeries(user));
    }

    @Operation(summary = "Test instructions page: structure, window and whether I may start")
    @GetMapping("/tests/{testId}")
    public ApiResponse<TestInfoDto> testInfo(@AuthenticationPrincipal AuthUser user, @PathVariable UUID testId) {
        return ApiResponse.ok(studentTestService.getInfo(user, testId));
    }
}
