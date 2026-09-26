package com.examprep.test.service;

import com.examprep.question.workflow.TestPinUpdater;
import com.examprep.test.event.TestPaperChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;

/**
 * Moves test question pins to a newly published question version (see {@link TestPinUpdater}).
 * Published tests that move get their cached paper evicted, so students see the fixed wording
 * on their next load; scoring is unaffected because only same-scoring versions move there.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestPinService implements TestPinUpdater {

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    private record Pin(UUID testQuestionId, UUID testId, String testStatus, Integer version) {
    }

    @Override
    public Result moveToVersion(UUID questionId, int newVersion, boolean paragraph, boolean includeLive,
                                IntPredicate compatible) {
        String column = paragraph ? "passage_version" : "question_version";
        String match = paragraph
                ? "tq.question_id in (select c.id from questions c where c.parent_id = ?)"
                : "tq.question_id = ?";
        List<Pin> pins = jdbc.query("select tq.id, tq.test_id, t.status, tq." + column
                        + " from test_questions tq join tests t on t.id = tq.test_id where " + match
                        + " and tq." + column + " is distinct from ?",
                (rs, i) -> new Pin(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        (Integer) rs.getObject(4)), questionId, newVersion);

        Set<UUID> drafts = new HashSet<>();
        Set<UUID> liveUpdated = new HashSet<>();
        Set<UUID> liveKept = new HashSet<>();
        for (Pin p : pins) {
            boolean draft = "DRAFT".equals(p.testStatus());
            boolean move = draft || (includeLive && p.version() != null && compatible.test(p.version()));
            if (move) {
                jdbc.update("update test_questions set " + column + " = ?, updated_at = now() where id = ?",
                        newVersion, p.testQuestionId());
                (draft ? drafts : liveUpdated).add(p.testId());
            } else {
                liveKept.add(p.testId());
            }
        }
        liveKept.removeAll(liveUpdated);
        liveUpdated.forEach(testId -> events.publishEvent(new TestPaperChangedEvent(testId, false)));
        if (!pins.isEmpty()) {
            log.info("Question {} v{}: {} draft test(s) and {} published test(s) moved, {} published test(s) kept",
                    questionId, newVersion, drafts.size(), liveUpdated.size(), liveKept.size());
        }
        return new Result(drafts.size(), liveUpdated.size(), liveKept.size());
    }
}
