package com.examprep.notification.listener;

import com.examprep.common.config.AppProperties;
import com.examprep.notification.entity.NotificationChannel;
import com.examprep.notification.entity.NotificationTemplate;
import com.examprep.notification.service.NotificationService;
import com.examprep.result.entity.Result;
import com.examprep.result.event.ResultEvents.ResultEvaluatedEvent;
import com.examprep.result.event.ResultEvents.ResultsFinalizedEvent;
import com.examprep.result.repository.ResultRepository;
import com.examprep.test.dto.TestLookupDtos.TestSnapshot;
import com.examprep.test.service.TestLookupService;
import com.examprep.user.dto.UserDto;
import com.examprep.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * "Your result is out" emails.
 * <ul>
 *   <li><b>Scheduled tests:</b> one email per ranked student once final ranks exist
 *       (paged, async, so 50k emails never block the ranking job).</li>
 *   <li><b>Always-open tests</b> that show results immediately: one email right after
 *       evaluation (with the live rank).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultNotificationListener {

    private static final int PAGE = 500;

    private final NotificationService notifications;
    private final ResultRepository results;
    private final TestLookupService tests;
    private final UserService users;
    private final AppProperties appProperties;

    @Async
    @TransactionalEventListener
    public void onFinalized(ResultsFinalizedEvent event) {
        TestSnapshot test = tests.snapshot(event.testId());
        int sent = 0;
        Page<Result> page;
        int index = 0;
        do {
            page = results.findByTestIdAndRankedTrue(event.testId(),
                    PageRequest.of(index++, PAGE, Sort.by("id")));
            for (Result r : page) {
                sent += send(test, r, r.getRank() == null ? "-" : r.getRank().toString(),
                        r.getPercentile() == null ? "-" : r.getPercentile().toPlainString()) ? 1 : 0;
            }
        } while (page.hasNext());
        log.info("Result emails for test {}: {} sent", event.testId(), sent);
    }

    @Async
    @TransactionalEventListener
    public void onEvaluated(ResultEvaluatedEvent event) {
        TestSnapshot test = tests.snapshot(event.testId());
        if (test.endAt() != null || !test.showResultImmediately()) {
            return;   // handled by onFinalized, or results are hidden
        }
        results.findById(event.resultId()).ifPresent(r -> send(test, r, "live", "-"));
    }

    private boolean send(TestSnapshot test, Result r, String rank, String percentile) {
        try {
            UserDto user = users.get(r.getUserId());
            notifications.send(user.id(), NotificationChannel.EMAIL, NotificationTemplate.RESULT_PUBLISHED,
                    user.email(), Map.of(
                            "name", user.fullName(),
                            "testTitle", test.title(),
                            "score", r.getScore().toPlainString(),
                            "maxScore", r.getMaxScore().toPlainString(),
                            "rank", rank,
                            "percentile", percentile,
                            "resultUrl", appProperties.frontendUrl() + "/attempts/" + r.getAttemptId() + "/result"));
            return true;
        } catch (RuntimeException e) {
            log.warn("Result email for {} failed: {}", r.getId(), e.getMessage());
            return false;
        }
    }
}
