package com.examprep.analytics;

import com.examprep.analytics.AnalyticsDtos.OverviewDto;
import com.examprep.analytics.AnalyticsDtos.SubjectStrength;
import com.examprep.analytics.AnalyticsDtos.Summary;
import com.examprep.analytics.AnalyticsDtos.TopicStrength;
import com.examprep.analytics.AnalyticsDtos.TrendPoint;
import com.examprep.catalog.dto.CatalogDtos.ExamDto;
import com.examprep.catalog.dto.TopicPath;
import com.examprep.catalog.service.CatalogQueryService;
import com.examprep.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Student performance analytics, aggregated in Postgres from the per-result JSONB
 * breakdowns ({@code results.topic_scores / section_scores}). A student's full history
 * is a handful of rows, so these queries stay cheap without pre-aggregation tables.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    static final int MIN_ATTEMPTS = 3;
    static final BigDecimal WEAK_BELOW = BigDecimal.valueOf(50);
    static final BigDecimal STRONG_FROM = BigDecimal.valueOf(75);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbc;
    private final CatalogQueryService catalog;

    public OverviewDto overview(UUID userId, String examCode) {
        UUID examId = examCode == null || examCode.isBlank() ? null : catalog.listActiveExams().exams().stream()
                .filter(e -> e.code().equalsIgnoreCase(examCode.trim())).map(ExamDto::id).findFirst()
                .orElseThrow(() -> NotFoundException.of("Exam", examCode));
        String examFilter = examId == null ? "" : " AND t.exam_id = ?";
        Object[] args = examId == null ? new Object[]{userId} : new Object[]{userId, examId};

        List<TrendPoint> trend = jdbc.query("""
                SELECT r.attempt_id, r.test_id, t.title, r.score, r.max_score, r.rank, r.percentile, r.is_ranked,
                       r.evaluated_at
                FROM results r JOIN tests t ON t.id = r.test_id
                WHERE r.user_id = ?""" + examFilter + " ORDER BY r.evaluated_at", (rs, i) -> new TrendPoint(
                rs.getObject("attempt_id", UUID.class), rs.getObject("test_id", UUID.class), rs.getString("title"),
                rs.getBigDecimal("score"), rs.getBigDecimal("max_score"),
                pct(rs.getBigDecimal("score"), rs.getBigDecimal("max_score")), (Integer) rs.getObject("rank"),
                rs.getBigDecimal("percentile"), !rs.getBoolean("is_ranked"),
                rs.getTimestamp("evaluated_at").toInstant()), args);

        List<Object[]> topicRows = jdbc.query("""
                SELECT (e->>'topicId')::uuid AS topic_id,
                       sum((e->>'total')::int) AS total, sum((e->>'attempted')::int) AS attempted,
                       sum((e->>'correct')::int) AS correct, sum((e->>'incorrect')::int) AS incorrect,
                       sum((e->>'score')::numeric) AS score, sum((e->>'maxScore')::numeric) AS max_score,
                       sum((e->>'timeSpentSeconds')::bigint) AS time
                FROM results r JOIN tests t ON t.id = r.test_id, jsonb_array_elements(r.topic_scores) e
                WHERE r.user_id = ?""" + examFilter + " GROUP BY 1", (rs, i) -> row(rs, "topic_id"), args);
        Map<UUID, TopicPath> paths = catalog.resolveTopics(topicRows.stream().map(r -> (UUID) r[0]).toList());
        List<TopicStrength> topics = topicRows.stream().map(r -> {
            TopicPath p = paths.get((UUID) r[0]);
            int attempted = (int) r[2];
            return new TopicStrength((UUID) r[0], p == null ? null : p.topicName(), p == null ? null : p.chapterName(),
                    p == null ? null : p.subjectName(), (int) r[1], attempted, (int) r[3], (int) r[4],
                    accuracy((int) r[3], attempted), pct((BigDecimal) r[5], (BigDecimal) r[6]),
                    attempted == 0 ? 0 : (long) r[7] / attempted);
        }).toList();

        List<Object[]> subjectRows = jdbc.query("""
                SELECT (e->>'subjectId')::uuid AS subject_id,
                       0 AS total, sum((e->>'attempted')::int) AS attempted,
                       sum((e->>'correct')::int) AS correct, sum((e->>'incorrect')::int) AS incorrect,
                       sum((e->>'score')::numeric) AS score, sum((e->>'maxScore')::numeric) AS max_score,
                       sum((e->>'timeSpentSeconds')::bigint) AS time
                FROM results r JOIN tests t ON t.id = r.test_id, jsonb_array_elements(r.section_scores) e
                WHERE r.user_id = ? AND e->>'subjectId' IS NOT NULL""" + examFilter + " GROUP BY 1",
                (rs, i) -> row(rs, "subject_id"), args);
        Map<UUID, String> subjectNames = subjectNames(paths, examId);
        List<SubjectStrength> subjects = subjectRows.stream()
                .map(r -> new SubjectStrength((UUID) r[0], subjectNames.get((UUID) r[0]), (int) r[2], (int) r[3],
                        (int) r[4], accuracy((int) r[3], (int) r[2]), pct((BigDecimal) r[5], (BigDecimal) r[6]),
                        (long) r[7]))
                .sorted(Comparator.comparing(SubjectStrength::accuracy))
                .toList();

        List<TopicStrength> eligible = topics.stream().filter(t -> t.attempted() >= MIN_ATTEMPTS).toList();
        List<TopicStrength> weak = eligible.stream().filter(t -> t.accuracy().compareTo(WEAK_BELOW) < 0)
                .sorted(Comparator.comparing(TopicStrength::accuracy)).limit(10).toList();
        List<TopicStrength> strong = eligible.stream().filter(t -> t.accuracy().compareTo(STRONG_FROM) >= 0)
                .sorted(Comparator.comparing(TopicStrength::accuracy).reversed()).limit(10).toList();

        return new OverviewDto(summary(trend, topics), trend, subjects, weak, strong,
                topics.stream().sorted(Comparator.comparing(TopicStrength::accuracy)).toList(), MIN_ATTEMPTS);
    }

    private static Summary summary(List<TrendPoint> trend, List<TopicStrength> topics) {
        List<TrendPoint> ranked = trend.stream().filter(t -> !t.practice()).toList();
        BigDecimal avgPct = average(trend.stream().map(TrendPoint::percentage).toList());
        BigDecimal best = ranked.stream().map(TrendPoint::percentile).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        int attempted = topics.stream().mapToInt(TopicStrength::attempted).sum();
        int correct = topics.stream().mapToInt(TopicStrength::correct).sum();
        long time = topics.stream().mapToLong(t -> t.avgSecondsPerQuestion() * t.attempted()).sum();
        return new Summary((int) trend.stream().map(TrendPoint::testId).distinct().count(), avgPct,
                accuracy(correct, attempted), best, attempted, time);
    }

    private Map<UUID, String> subjectNames(Map<UUID, TopicPath> paths, UUID examId) {
        Map<UUID, String> names = new HashMap<>();
        paths.values().forEach(p -> names.put(p.subjectId(), p.subjectName()));
        List<ExamDto> exams = examId == null ? catalog.listActiveExams().exams()
                : catalog.listActiveExams().exams().stream().filter(e -> e.id().equals(examId)).toList();
        for (ExamDto e : exams) {
            catalog.getActiveTree(e.code()).subjects().forEach(s -> names.putIfAbsent(s.id(), s.name()));
        }
        return names;
    }

    private static Object[] row(ResultSet rs, String idColumn) throws SQLException {
        return new Object[]{rs.getObject(idColumn, UUID.class), rs.getInt("total"), rs.getInt("attempted"),
                rs.getInt("correct"), rs.getInt("incorrect"), rs.getBigDecimal("score"),
                rs.getBigDecimal("max_score"), rs.getLong("time")};
    }

    static BigDecimal accuracy(int correct, int attempted) {
        return attempted == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(correct).multiply(HUNDRED).divide(BigDecimal.valueOf(attempted), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return part == null || whole == null || whole.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
