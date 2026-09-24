package com.examprep.attempt.controller;

import com.examprep.attempt.dto.AttemptDtos.AntiCheatDto;
import com.examprep.attempt.dto.AttemptDtos.AttemptEventsRequest;
import com.examprep.attempt.dto.AttemptDtos.AttemptSessionDto;
import com.examprep.attempt.dto.AttemptDtos.AttemptSummaryDto;
import com.examprep.attempt.dto.AttemptDtos.AutosaveRequest;
import com.examprep.attempt.dto.AttemptDtos.AutosaveResult;
import com.examprep.attempt.dto.AttemptDtos.TimeDto;
import com.examprep.attempt.entity.SubmitType;
import com.examprep.attempt.service.AntiCheatService;
import com.examprep.attempt.service.AttemptService;
import com.examprep.attempt.service.AutosaveService;
import com.examprep.attempt.service.SubmissionService;
import com.examprep.common.api.ApiResponse;
import com.examprep.common.ratelimit.RateLimit;
import com.examprep.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Test-taking API used by the exam interface.
 * <pre>
 * POST /tests/{testId}/attempts      start, or resume the running attempt (idempotent)
 * GET  /attempts/{id}                full session: paper, saved answers, server time
 * PUT  /attempts/{id}/answers        autosave batch (every 10-15 s, plus on navigation)
 * GET  /attempts/{id}/time           cheap time sync
 * POST /attempts/{id}/events         anti-cheat signals
 * POST /attempts/{id}/submit         final submit (idempotent)
 * </pre>
 */
@Tag(name = "Attempts (test engine)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attempts;
    private final AutosaveService autosave;
    private final AntiCheatService antiCheat;
    private final SubmissionService submissions;

    @Operation(summary = "Start the test, or resume the running attempt")
    @PostMapping("/tests/{testId}/attempts")
    @RateLimit(name = "attempt-start", limit = 20, windowSeconds = 60, key = RateLimit.KeyType.USER)
    public ApiResponse<AttemptSessionDto> start(@AuthenticationPrincipal AuthUser user, @PathVariable UUID testId,
                                                HttpServletRequest request) {
        return ApiResponse.ok(attempts.start(user, testId, request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT)));
    }

    @Operation(summary = "My attempts at a test")
    @GetMapping("/tests/{testId}/attempts")
    public ApiResponse<List<AttemptSummaryDto>> mine(@AuthenticationPrincipal AuthUser user,
                                                     @PathVariable UUID testId) {
        return ApiResponse.ok(attempts.myAttempts(user, testId));
    }

    @Operation(summary = "Session for rendering or resuming (paper + saved answers + server time)")
    @GetMapping("/attempts/{attemptId}")
    public ApiResponse<AttemptSessionDto> session(@AuthenticationPrincipal AuthUser user,
                                                  @PathVariable UUID attemptId) {
        return ApiResponse.ok(attempts.session(user, attemptId));
    }

    @Operation(summary = "Autosave a batch of answer changes (Redis only)")
    @PutMapping("/attempts/{attemptId}/answers")
    @RateLimit(name = "autosave", limit = 60, windowSeconds = 60, key = RateLimit.KeyType.USER)
    public ApiResponse<AutosaveResult> autosave(@AuthenticationPrincipal AuthUser user, @PathVariable UUID attemptId,
                                                @Valid @RequestBody AutosaveRequest request) {
        return ApiResponse.ok(autosave.save(user, attemptId, request));
    }

    @Operation(summary = "Server time and remaining seconds")
    @GetMapping("/attempts/{attemptId}/time")
    @RateLimit(name = "attempt-time", limit = 60, windowSeconds = 60, key = RateLimit.KeyType.USER)
    public ApiResponse<TimeDto> time(@AuthenticationPrincipal AuthUser user, @PathVariable UUID attemptId) {
        return ApiResponse.ok(attempts.time(user, attemptId));
    }

    @Operation(summary = "Report anti-cheat events (tab switch, fullscreen exit, ...)")
    @PostMapping("/attempts/{attemptId}/events")
    @RateLimit(name = "attempt-events", limit = 60, windowSeconds = 60, key = RateLimit.KeyType.USER)
    public ApiResponse<AntiCheatDto> events(@AuthenticationPrincipal AuthUser user, @PathVariable UUID attemptId,
                                            @Valid @RequestBody AttemptEventsRequest request) {
        return ApiResponse.ok(antiCheat.record(user, attemptId, request));
    }

    @Operation(summary = "Submit the attempt (idempotent)")
    @PostMapping("/attempts/{attemptId}/submit")
    @RateLimit(name = "attempt-submit", limit = 20, windowSeconds = 60, key = RateLimit.KeyType.USER)
    public ApiResponse<AttemptSummaryDto> submit(@AuthenticationPrincipal AuthUser user,
                                                 @PathVariable UUID attemptId) {
        return ApiResponse.ok(submissions.submit(attemptId, user.id(), SubmitType.MANUAL, null).orElseThrow());
    }
}
