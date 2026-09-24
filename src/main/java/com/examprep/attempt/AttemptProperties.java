package com.examprep.attempt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tunables of the test-taking engine ({@code app.attempt.*}).
 *
 * @param autosaveGrace         autosaves are still accepted this long after the deadline (network latency of the final save)
 * @param autoSubmitGrace       the scheduler force-submits this long after the deadline, giving the client's own
 *                              final save and submit a chance to arrive first
 * @param maxTabSwitches        auto-submit once a student exceeds this many tab switches; 0 disables it
 * @param paperCacheTtl         Redis TTL of a built test paper
 * @param paperLocalTtl         per-instance in-memory TTL of a paper (keeps autosave validation off Redis)
 * @param autoSubmitBatch       max attempts force-submitted per scheduler tick
 * @param autoSubmitConcurrency parallel submissions per tick (keep below the DB pool size)
 */
@ConfigurationProperties(prefix = "app.attempt")
public record AttemptProperties(
        @DefaultValue("15s") Duration autosaveGrace,
        @DefaultValue("30s") Duration autoSubmitGrace,
        @DefaultValue("0") int maxTabSwitches,
        @DefaultValue("6h") Duration paperCacheTtl,
        @DefaultValue("60s") Duration paperLocalTtl,
        @DefaultValue("1h") Duration submittedStateTtl,
        @DefaultValue("500") int maxBufferedEvents,
        @DefaultValue("200") int maxChangesPerAutosave,
        @DefaultValue("1000") int autoSubmitBatch,
        @DefaultValue("16") int autoSubmitConcurrency) {
}
