package com.examprep.result.service;

import com.examprep.attempt.entity.AttemptStatus;
import com.examprep.attempt.service.AttemptEvaluationAccess;
import com.examprep.attempt.service.AttemptEvaluationAccess.AnswerRow;
import com.examprep.attempt.service.AttemptEvaluationAccess.AttemptSnapshot;
import com.examprep.catalog.dto.TopicPath;
import com.examprep.catalog.service.CatalogQueryService;
import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.question.dto.QuestionPin;
import com.examprep.question.dto.ReviewQuestionView;
import com.examprep.question.service.QuestionLookupService;
import com.examprep.result.dto.ResultDtos.AdminResultRowDto;
import com.examprep.result.dto.ResultDtos.ComparisonDto;
import com.examprep.result.dto.ResultDtos.LeaderboardDto;
import com.examprep.result.dto.ResultDtos.LeaderboardEntryDto;
import com.examprep.result.dto.ResultDtos.PerformanceDto;
import com.examprep.result.dto.ResultDtos.ResultDto;
import com.examprep.result.dto.ResultDtos.ResultStatus;
import com.examprep.result.dto.ResultDtos.ReviewItemDto;
import com.examprep.result.dto.ResultDtos.ReviewSectionDto;
import com.examprep.result.dto.ResultDtos.SectionComparisonDto;
import com.examprep.result.dto.ResultDtos.SectionResultDto;
import com.examprep.result.dto.ResultDtos.SolutionReviewDto;
import com.examprep.result.dto.ResultDtos.TopicResultDto;
import com.examprep.result.entity.Result;
import com.examprep.result.model.ScoreBreakdowns.SectionScore;
import com.examprep.result.model.ScoreBreakdowns.TopicScore;
import com.examprep.result.repository.ResultRepository;
import com.examprep.result.scoring.ScoringEngine.QuestionSpec;
import com.examprep.security.AuthUser;
import com.examprep.test.dto.TestLookupDtos.TestSnapshot;
import com.examprep.test.service.TestLookupService;
import com.examprep.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Result page, solution review, leaderboard and comparison, all behind one set of
 * visibility rules:
 * <pre>
 *  result visible     = staff OR test.showResultImmediately OR window closed OR ranks final
 *  solutions visible  = result visible AND (always-open test OR window closed)
 * </pre>
 * Solutions stay hidden until a scheduled test closes, so early finishers cannot leak
 * answers to students who are still writing.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResultQueryService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ResultRepository results;
    private final AttemptEvaluationAccess attempts;
    private final TestLookupService tests;
    private final EvaluationSpecCache specs;
    private final QuestionLookupService questions;
    private final CatalogQueryService catalog;
    private final LeaderboardService leaderboard;
    private final UserService users;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    private record Visibility(boolean result, boolean solutions, Instant solutionsAt) {
    }

    // ------------------------------------------------------------------ result page

    public ResultDto result(AuthUser user, UUID attemptId) {
        AttemptSnapshot attempt = ownAttempt(user, attemptId);
        TestSnapshot test = tests.snapshot(attempt.testId());
        if (attempt.status() == AttemptStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.CONFLICT, "Submit the attempt to see its result");
        }
        Result r = results.findByAttemptId(attemptId).orElse(null);
        if (r == null) {
            return stub(attempt, test, ResultStatus.EVALUATING);
        }
        Visibility v = visibility(user, test, r);
        if (!v.result()) {
            return stub(attempt, test, ResultStatus.AWAITING_PUBLICATION);
        }

        Integer rank = r.getRank();
        BigDecimal percentile = r.getPercentile();
        Long candidates = null;
        if (r.isRanked()) {
            if (r.isRankFinal()) {
                candidates = results.countByTestIdAndRankedTrue(test.id());
            } else {
                Optional<LeaderboardService.LiveRank> live = leaderboard.liveRank(test.id(), r.getUserId());
                if (live.isPresent()) {
                    rank = live.get().rank();
                    percentile = live.get().percentile();
                    candidates = live.get().totalCandidates();
                }
            }
        }

        Map<UUID, TopicPath> paths = catalog.resolveTopics(r.getTopicScores().stream()
                .map(TopicScore::topicId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<TopicResultDto> topics = r.getTopicScores().stream()
                .map(t -> {
                    TopicPath p = paths.get(t.topicId());
                    return new TopicResultDto(t.topicId(), p == null ? null : p.topicName(),
                            p == null ? null : p.chapterName(), p == null ? null : p.subjectName(), t.total(),
                            t.attempted(), t.correct(), t.incorrect(), t.partial(), t.score(), t.maxScore(),
                            accuracy(t.correct(), t.attempted()), t.timeSpentSeconds());
                })
                .sorted(Comparator.comparing(TopicResultDto::accuracy))
                .toList();
        List<SectionResultDto> sections = r.getSectionScores().stream()
                .map(s -> new SectionResultDto(s.sectionId(), s.name(), s.subjectId(), s.total(), s.attempted(),
                        s.correct(), s.incorrect(), s.partial(), s.unattempted(), s.score(), s.maxScore(),
                        accuracy(s.correct(), s.attempted()), s.timeSpentSeconds()))
                .toList();

        return new ResultDto(attemptId, test.id(), test.title(), ResultStatus.READY, attempt.attemptNo(), r.isRanked(),
                r.getScore(), r.getMaxScore(), percentage(r.getScore(), r.getMaxScore()), rank, percentile,
                r.isRankFinal(), candidates, r.getCorrectCount(), r.getIncorrectCount(), r.getPartialCount(),
                r.getUnattemptedCount(), r.getAccuracy(), r.getTimeTakenSeconds(), r.getEvaluatedAt(), sections,
                topics, v.solutions(), v.solutionsAt());
    }

    // ------------------------------------------------------------------ solutions

    public SolutionReviewDto solutions(AuthUser user, UUID attemptId) {
        AttemptSnapshot attempt = ownAttempt(user, attemptId);
        TestSnapshot test = tests.snapshot(attempt.testId());
        Result r = results.findByAttemptId(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "Result is not ready yet"));
        Visibility v = visibility(user, test, r);
        if (!v.solutions()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, v.solutionsAt() == null
                    ? "Solutions are not available yet"
                    : "Solutions will be available after the test closes at " + v.solutionsAt());
        }

        EvaluationSpecCache.Spec spec = specs.get(test.id());
        Map<UUID, ReviewQuestionView> views = questions.findReviewViews(spec.questions().stream()
                .map(q -> new QuestionPin(q.questionId(), spec.pins().get(q.questionId()).questionVersion())).toList());
        Map<UUID, AnswerRow> answers = attempts.answers(attemptId).stream()
                .collect(Collectors.toMap(AnswerRow::questionId, a -> a));
        Map<UUID, Integer> passagePins = new java.util.HashMap<>();
        views.values().forEach(view -> {
            if (view.parentId() != null) {
                passagePins.putIfAbsent(view.parentId(),
                        Objects.requireNonNullElse(spec.pins().get(view.id()).passageVersion(), 1));
            }
        });
        var passages = questions.findPassages(passagePins);

        Map<UUID, List<QuestionSpec>> bySection = spec.questions().stream()
                .collect(Collectors.groupingBy(QuestionSpec::sectionId));
        int number = 0;
        List<ReviewSectionDto> sections = new ArrayList<>();
        for (var section : spec.sections()) {
            List<ReviewItemDto> items = new ArrayList<>();
            for (QuestionSpec q : bySection.getOrDefault(section.sectionId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(QuestionSpec::order)).toList()) {
                ReviewQuestionView view = views.get(q.questionId());
                AnswerRow a = answers.get(q.questionId());
                items.add(new ReviewItemDto(++number, q.questionId(), q.type(), view.parentId(), view.text(),
                        view.images(), view.options(), view.matchLeft(), view.matchRight(),
                        a == null ? null : a.answer(),
                        a == null ? com.examprep.attempt.entity.AnswerState.NOT_VISITED : a.state(),
                        view.answerKey(), a == null || a.outcome() == null ? "UNATTEMPTED" : a.outcome(),
                        a == null || a.marksAwarded() == null ? BigDecimal.ZERO : a.marksAwarded(), q.marks(),
                        q.negativeMarks(), a == null ? 0 : a.timeSpentSeconds(), view.solution(), view.language(),
                        view.translations()));
            }
            sections.add(new ReviewSectionDto(section.sectionId(), section.name(), items));
        }
        return new SolutionReviewDto(attemptId, test.id(), sections, passages);
    }

    // ------------------------------------------------------------------ leaderboard

    public LeaderboardDto leaderboard(AuthUser user, UUID testId, int limit) {
        TestSnapshot test = tests.snapshot(testId);
        if (!visibility(user, test, null).result()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "The leaderboard is published after the test closes");
        }
        List<LeaderboardService.Entry> top = leaderboard.top(testId, Math.min(Math.max(limit, 1), 100));
        Map<UUID, String> names = users.findNames(top.stream().map(LeaderboardService.Entry::userId).toList());
        List<LeaderboardEntryDto> entries = top.stream()
                .map(e -> new LeaderboardEntryDto(e.rank(), shortName(names.get(e.userId())), e.score(),
                        e.timeTakenSeconds(), e.userId().equals(user.id())))
                .toList();

        LeaderboardEntryDto me = null;
        BigDecimal myPercentile = null;
        Optional<LeaderboardService.LiveRank> mine = leaderboard.liveRank(testId, user.id());
        if (mine.isPresent()) {
            Result my = results.findFirstByTestIdAndUserIdAndRankedTrue(testId, user.id()).orElse(null);
            if (my != null) {
                me = new LeaderboardEntryDto(my.isRankFinal() ? my.getRank() : mine.get().rank(), "You", my.getScore(),
                        my.getTimeTakenSeconds(), true);
                myPercentile = my.isRankFinal() ? my.getPercentile() : mine.get().percentile();
            }
        }
        return new LeaderboardDto(testId, leaderboard.size(testId), entries, me, myPercentile);
    }

    // ------------------------------------------------------------------ comparison

    public ComparisonDto comparison(AuthUser user, UUID attemptId) {
        AttemptSnapshot attempt = ownAttempt(user, attemptId);
        TestSnapshot test = tests.snapshot(attempt.testId());
        Result me = results.findByAttemptId(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "Result is not ready yet"));
        if (!visibility(user, test, me).result()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Results are not published yet");
        }
        UUID testId = test.id();

        Map<String, Object> avg = jdbc.queryForMap("""
                SELECT count(*) AS n, avg(score) AS score, avg(accuracy) AS accuracy, avg(time_taken_seconds) AS time,
                       avg(correct_count) AS correct, avg(incorrect_count) AS incorrect
                FROM results WHERE test_id = ? AND is_ranked
                """, testId);
        Result topper = results.findByTestIdAndRankedTrue(testId,
                        org.springframework.data.domain.PageRequest.of(0, 1,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Order.desc("score"),
                                        org.springframework.data.domain.Sort.Order.asc("timeTakenSeconds"))))
                .stream().findFirst().orElse(me);
        Map<String, BigDecimal> sectionAvg = new HashMap<>();
        jdbc.query("""
                SELECT e->>'sectionId' AS sid, avg((e->>'score')::numeric) AS avg
                FROM results r, jsonb_array_elements(r.section_scores) e
                WHERE r.test_id = ? AND r.is_ranked GROUP BY 1
                """, rs -> {
            sectionAvg.put(rs.getString("sid"), rs.getBigDecimal("avg"));
        }, testId);
        Map<UUID, SectionScore> topperSections = topper.getSectionScores().stream()
                .collect(Collectors.toMap(SectionScore::sectionId, s -> s));

        List<SectionComparisonDto> sections = me.getSectionScores().stream()
                .map(s -> new SectionComparisonDto(s.sectionId(), s.name(), s.score(),
                        Optional.ofNullable(topperSections.get(s.sectionId())).map(SectionScore::score).orElse(null),
                        round(sectionAvg.get(s.sectionId().toString())), s.maxScore()))
                .toList();
        return new ComparisonDto(attemptId, testId, ((Number) avg.get("n")).longValue(),
                performance(me), performance(topper),
                new PerformanceDto(round((BigDecimal) avg.get("score")), round((BigDecimal) avg.get("accuracy")),
                        avg.get("time") == null ? null : ((Number) avg.get("time")).intValue(),
                        round((BigDecimal) avg.get("correct")), round((BigDecimal) avg.get("incorrect"))),
                sections);
    }

    // ------------------------------------------------------------------ admin

    public PageResponse<AdminResultRowDto> adminResults(UUID testId, Pageable pageable) {
        Page<Result> page = results.findByTestId(testId, pageable);
        Map<UUID, String> names = users.findNames(page.getContent().stream().map(Result::getUserId).toList());
        return PageResponse.of(page, r -> new AdminResultRowDto(r.getId(), r.getAttemptId(), r.getUserId(),
                names.get(r.getUserId()), r.getScore(), r.getMaxScore(), r.getRank(), r.getPercentile(),
                r.isRanked(), r.getAccuracy(), r.getTimeTakenSeconds(), r.getEvaluatedAt()));
    }

    // ------------------------------------------------------------------ helpers

    private AttemptSnapshot ownAttempt(AuthUser user, UUID attemptId) {
        AttemptSnapshot a = attempts.get(attemptId);
        if (!user.isStaff() && !a.userId().equals(user.id())) {
            throw NotFoundException.of("Attempt", attemptId);
        }
        return a;
    }

    private Visibility visibility(AuthUser user, TestSnapshot test, Result r) {
        Instant now = Instant.now(clock);
        boolean windowClosed = test.endAt() != null && !now.isBefore(test.endAt());
        boolean result = user.isStaff() || test.showResultImmediately() || windowClosed
                || (r != null && r.isRankFinal());
        boolean solutions = user.isStaff() || (result && (test.endAt() == null || windowClosed));
        Instant solutionsAt = test.endAt() == null ? null : test.endAt();
        return new Visibility(result, solutions, solutions ? null : solutionsAt);
    }

    private static ResultDto stub(AttemptSnapshot a, TestSnapshot t, ResultStatus status) {
        return new ResultDto(a.attemptId(), t.id(), t.title(), status, a.attemptNo(), a.attemptNo() == 1, null,
                t.totalMarks(), null, null, null, false, null, null, null, null, null, null, null, null,
                List.of(), List.of(), false, t.endAt());
    }

    private static PerformanceDto performance(Result r) {
        return new PerformanceDto(r.getScore(), r.getAccuracy(), r.getTimeTakenSeconds(),
                BigDecimal.valueOf(r.getCorrectCount()), BigDecimal.valueOf(r.getIncorrectCount()));
    }

    static BigDecimal accuracy(int correct, int attempted) {
        return attempted == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(correct).multiply(HUNDRED).divide(BigDecimal.valueOf(attempted), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentage(BigDecimal score, BigDecimal max) {
        return max == null || max.signum() == 0 ? BigDecimal.ZERO
                : score.multiply(HUNDRED).divide(max, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal round(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** "Riya Sharma" becomes "Riya S."; single names are kept. */
    static String shortName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Student";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
