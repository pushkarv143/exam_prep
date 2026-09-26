package com.examprep.result.controller;

import com.examprep.common.idempotency.Idempotent;
import com.examprep.common.api.ApiResponse;
import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.result.dto.ResultDtos.AdminResultRowDto;
import com.examprep.result.service.EvaluationService;
import com.examprep.result.service.RankingService;
import com.examprep.result.service.ResultQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Results (admin)")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("@perm.has('admin.access')")
public class AdminResultController {

    private final ResultQueryService resultService;
    private final RankingService rankingService;
    private final EvaluationService evaluationService;

    @PreAuthorize("@perm.has('result.view')")
    @GetMapping("/tests/{testId}/results")
    public ApiResponse<PageResponse<AdminResultRowDto>> results(
            @PathVariable UUID testId,
            @ParameterObject @PageableDefault(size = 50, sort = "score", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(resultService.adminResults(testId, pageable));
    }

    @Operation(summary = "Compute final ranks and percentiles now (e.g. for always-open tests)")
    @PreAuthorize("@perm.has('result.finalize')")
    @Idempotent
    @PostMapping("/tests/{testId}/rankings/finalize")
    public ApiResponse<Map<String, Integer>> finalizeRanks(@PathVariable UUID testId) {
        int ranked = rankingService.finalizeRanks(testId);
        if (ranked < 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Ranking is already running for this test");
        }
        return ApiResponse.ok(Map.of("rankedCandidates", ranked));
    }

    @Operation(summary = "Force re-evaluation of one attempt (e.g. after a scoring fix)")
    @PreAuthorize("@perm.has('result.regenerate')")
    @Idempotent
    @PostMapping("/attempts/{attemptId}/re-evaluate")
    public ApiResponse<Map<String, Boolean>> reEvaluate(@PathVariable UUID attemptId) {
        return ApiResponse.ok(Map.of("evaluated", evaluationService.evaluate(attemptId, true)));
    }
}
