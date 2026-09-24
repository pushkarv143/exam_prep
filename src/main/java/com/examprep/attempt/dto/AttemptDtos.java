package com.examprep.attempt.dto;

import com.examprep.attempt.entity.AnswerState;
import com.examprep.attempt.entity.AttemptEventType;
import com.examprep.attempt.entity.AttemptStatus;
import com.examprep.attempt.entity.SubmitType;
import com.examprep.attempt.model.StudentAnswer;
import com.examprep.attempt.paper.PaperDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AttemptDtos {

    private AttemptDtos() {
    }

    /**
     * Everything the exam UI needs to render or resume.
     *
     * <p>Timer: the client computes {@code offset = serverNow - clientNow} once and shows
     * {@code deadlineAt - (clientNow + offset)}. Every autosave response re-syncs it.
     *
     * @param lastSeq highest autosave seq stored. The client must continue from {@code lastSeq + 1}
     *                (it matters if the browser's localStorage was cleared).
     */
    public record AttemptSessionDto(UUID attemptId, UUID testId, int attemptNo, AttemptStatus status,
                                    Instant startedAt, Instant deadlineAt, Instant serverNow, long remainingSeconds,
                                    PaperDto paper, Map<UUID, AnswerStateDto> answers, long lastSeq,
                                    AntiCheatDto antiCheat) {
    }

    public record AnswerStateDto(StudentAnswer answer, AnswerState state, int timeSpentSeconds, int visits,
                                 long seq) {
    }

    /**
     * One question's latest state, as tracked by the client.
     *
     * @param seq              monotonically increasing per attempt (client counter). Older seqs lose.
     * @param answer           null or empty = "clear response"
     * @param timeSpentSeconds cumulative time on this question. The server keeps the max and
     *                         <em>clamps</em> it to the elapsed attempt time. An implausible
     *                         value (buggy client clock) is corrected, never rejected, so one
     *                         bad field can't make a whole batch of answers fail.
     * @param visits           cumulative number of times the question was opened (clamped)
     */
    public record AnswerChange(
            @NotNull UUID questionId,
            @PositiveOrZero long seq,
            @Valid StudentAnswer answer,
            boolean markedForReview,
            @PositiveOrZero int timeSpentSeconds,
            @PositiveOrZero int visits) {
    }

    /** Batch of changes since the last successful autosave. An empty list works as a heartbeat / time sync. */
    public record AutosaveRequest(@NotNull @Size(max = 500) List<@Valid AnswerChange> changes) {
    }

    public record RejectedChange(UUID questionId, String reason) {
    }

    public record AutosaveResult(int applied, List<RejectedChange> rejected, Instant serverNow,
                                 long remainingSeconds) {
    }

    public record ClientEvent(@NotNull AttemptEventType type, Instant clientTs,
                              @Size(max = 10) Map<@Size(max = 40) String, @Size(max = 200) String> details) {
    }

    public record AttemptEventsRequest(@NotNull @Size(max = 50) List<@Valid ClientEvent> events) {
    }

    /** @param maxTabSwitches 0 = no limit. {@code autoSubmitted} tells the UI to go to the result page. */
    public record AntiCheatDto(int tabSwitchCount, int fullscreenExitCount, int maxTabSwitches,
                               boolean autoSubmitted) {
    }

    public record TimeDto(AttemptStatus status, Instant serverNow, Instant deadlineAt, long remainingSeconds) {
    }

    public record AttemptSummaryDto(UUID attemptId, UUID testId, int attemptNo, AttemptStatus status,
                                    SubmitType submitType, Instant startedAt, Instant deadlineAt,
                                    Instant submittedAt, int answeredCount, int markedCount, int visitedCount,
                                    int tabSwitchCount, int fullscreenExitCount) {
    }
}
