package com.examprep.attempt.service;

import com.examprep.attempt.AttemptProperties;
import com.examprep.attempt.dto.AttemptDtos.AntiCheatDto;
import com.examprep.attempt.dto.AttemptDtos.AttemptEventsRequest;
import com.examprep.attempt.dto.AttemptDtos.ClientEvent;
import com.examprep.attempt.entity.AttemptEventType;
import com.examprep.attempt.entity.SubmitType;
import com.examprep.attempt.store.AttemptRedisStore;
import com.examprep.attempt.store.AttemptRedisStore.AttemptMeta;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.security.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records proctoring signals (tab switches, fullscreen exits, copy/paste, ...).
 * Counters live in the attempt's Redis meta, and events are buffered in Redis and
 * flushed to {@code attempt_events} on submit.
 *
 * <p>Browser signals can be suppressed by a determined user, so treat them as
 * deterrence plus evidence for review, not proof. The optional hard limit
 * ({@code app.attempt.max-tab-switches}) auto-submits the attempt when exceeded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AntiCheatService {

    private final AttemptAccess access;
    private final AttemptRedisStore store;
    private final SubmissionService submissions;
    private final AttemptProperties props;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AntiCheatDto record(AuthUser user, UUID attemptId, AttemptEventsRequest request) {
        Instant now = Instant.now(clock);
        AttemptMeta meta = access.requireOwnInProgress(user.id(), attemptId, now);

        int tabDelta = 0;
        int fsDelta = 0;
        List<String> buffered = new ArrayList<>(request.events().size());
        for (ClientEvent e : request.events()) {
            if (!e.type().isClientReportable()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, e.type() + " cannot be reported by the client");
            }
            if (e.type() == AttemptEventType.TAB_SWITCH) {
                tabDelta++;
            } else if (e.type() == AttemptEventType.FULLSCREEN_EXIT) {
                fsDelta++;
            }
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("type", e.type().name());
            json.put("serverTs", now.toString());
            if (e.clientTs() != null) {
                json.put("clientTs", e.clientTs().toString());
            }
            if (e.details() != null && !e.details().isEmpty()) {
                json.put("details", e.details());
            }
            buffered.add(toJson(json));
        }
        store.appendEvents(attemptId, buffered);
        long tabs = tabDelta > 0 ? store.incrementCounter(attemptId, "tab", tabDelta) : meta.tabSwitches();
        long fs = fsDelta > 0 ? store.incrementCounter(attemptId, "fs", fsDelta) : meta.fullscreenExits();

        boolean autoSubmitted = false;
        if (props.maxTabSwitches() > 0 && tabs > props.maxTabSwitches()) {
            log.warn("Attempt {} exceeded tab-switch limit ({} > {}); auto-submitting", attemptId, tabs,
                    props.maxTabSwitches());
            submissions.submit(attemptId, null, SubmitType.AUTO, "Tab-switch limit exceeded (" + tabs + ")");
            autoSubmitted = true;
        }
        return new AntiCheatDto((int) tabs, (int) fs, props.maxTabSwitches(), autoSubmitted);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
