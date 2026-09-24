package com.examprep.analytics;

import com.examprep.analytics.AnalyticsDtos.OverviewDto;
import com.examprep.common.api.ApiResponse;
import com.examprep.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Analytics")
@RestController
@RequestMapping("/api/v1/me/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analytics;

    @Operation(summary = "Score trend, subject strength, weak/strong topics (optionally for one exam)")
    @GetMapping
    public ApiResponse<OverviewDto> overview(@AuthenticationPrincipal AuthUser user,
                                             @RequestParam(required = false) String examCode) {
        return ApiResponse.ok(analytics.overview(user.id(), examCode));
    }
}
