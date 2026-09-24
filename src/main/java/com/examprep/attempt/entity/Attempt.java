package com.examprep.attempt.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One sitting of a test by a student. {@link #deadlineAt} is fixed at start time
 * (server clock) and is the only timer that matters. Client timers are display only.
 */
@Getter
@Setter
@Entity
@Table(name = "attempts")
public class Attempt extends BaseEntity {

    @Column(name = "test_id", nullable = false, updatable = false)
    private UUID testId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "submit_type")
    private SubmitType submitType;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    /** Seeds the per-student question/option shuffle, so a resume shows the identical order. */
    @Column(name = "shuffle_seed", nullable = false, updatable = false)
    private long shuffleSeed;

    @Column(name = "tab_switch_count", nullable = false)
    private int tabSwitchCount;

    @Column(name = "fullscreen_exit_count", nullable = false)
    private int fullscreenExitCount;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "user_agent")
    private String userAgent;
}
