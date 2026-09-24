package com.examprep.result.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.result.dto.ResultDtos.ComparisonDto;
import com.examprep.result.dto.ResultDtos.LeaderboardDto;
import com.examprep.result.dto.ResultDtos.ResultDto;
import com.examprep.result.dto.ResultDtos.SolutionReviewDto;
import com.examprep.result.service.ResultQueryService;
import com.examprep.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Results")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResultController {

    private final ResultQueryService resultService;

    @Operation(summary = "Result page (status EVALUATING / AWAITING_PUBLICATION / READY)")
    @GetMapping("/attempts/{attemptId}/result")
    public ApiResponse<ResultDto> result(@AuthenticationPrincipal AuthUser user, @PathVariable UUID attemptId) {
        return ApiResponse.ok(resultService.result(user, attemptId));
    }

    @Operation(summary = "Solution review with correct answers (after the test window closes)")
    @GetMapping("/attempts/{attemptId}/solutions")
    public ApiResponse<SolutionReviewDto> solutions(@AuthenticationPrincipal AuthUser user,
                                                    @PathVariable UUID attemptId) {
        return ApiResponse.ok(resultService.solutions(user, attemptId));
    }

    @Operation(summary = "Me vs topper vs average, overall and per section")
    @GetMapping("/attempts/{attemptId}/comparison")
    public ApiResponse<ComparisonDto> comparison(@AuthenticationPrincipal AuthUser user,
                                                 @PathVariable UUID attemptId) {
        return ApiResponse.ok(resultService.comparison(user, attemptId));
    }

    @Operation(summary = "Leaderboard (top N, max 100) plus my own rank")
    @GetMapping("/tests/{testId}/leaderboard")
    public ApiResponse<LeaderboardDto> leaderboard(@AuthenticationPrincipal AuthUser user, @PathVariable UUID testId,
                                                   @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(resultService.leaderboard(user, testId, limit));
    }
}
