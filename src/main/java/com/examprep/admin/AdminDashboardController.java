package com.examprep.admin;

import com.examprep.admin.AdminDtos.DashboardDto;
import com.examprep.admin.AdminDtos.TestStatsDto;
import com.examprep.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin dashboard & reports")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboard;
    private final TestStatsService testStats;

    @Operation(summary = "Platform KPIs: users, tests, live attempts, revenue (with 30-day daily series)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardDto> dashboard() {
        return ApiResponse.ok(dashboard.dashboard());
    }

    @Operation(summary = "Test-wise statistics: score summary, distribution, per-question difficulty analysis")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @GetMapping("/tests/{testId}/stats")
    public ApiResponse<TestStatsDto> testStats(@PathVariable UUID testId) {
        return ApiResponse.ok(testStats.stats(testId));
    }
}
