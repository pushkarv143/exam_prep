package com.examprep.admin;

import com.examprep.admin.AdminDtos.Bucket;
import com.examprep.admin.AdminDtos.QuestionStat;
import com.examprep.admin.AdminDtos.ScoreStats;
import com.examprep.admin.AdminDtos.TestStatsDto;
import com.examprep.question.dto.QuestionSummaryDto;
import com.examprep.question.service.QuestionLookupService;
import com.examprep.test.dto.TestLookupDtos.QuestionSlot;
import com.examprep.test.dto.TestLookupDtos.SectionSpec;
import com.examprep.test.dto.TestLookupDtos.TestStructure;
import com.examprep.test.service.TestLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Test-wise statistics for admins and teachers: score summary, distribution, and a
 * per-question difficulty analysis that flags questions that are too hard, too easy or
 * mostly skipped. This helps curate the question bank.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestStatsService {

    private static final int BUCKETS = 10;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbc;
    private final TestLookupService tests;
    private final QuestionLookupService questions;

    public TestStatsDto stats(UUID testId) {
        TestStructure structure = tests.structure(testId);
        BigDecimal max = structure.test().totalMarks();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("SELECT status, count(*) FROM attempts WHERE test_id = ? GROUP BY status",
                rs -> {
                    byStatus.put(rs.getString(1), rs.getLong(2));
                }, testId);

        ScoreStats scores = jdbc.queryForObject("""
                SELECT count(*) AS n, avg(score) AS avg, percentile_cont(0.5) WITHIN GROUP (ORDER BY score) AS median,
                       max(score) AS hi, min(score) AS lo, stddev_pop(score) AS sd, avg(accuracy) AS acc,
                       avg(time_taken_seconds) AS t
                FROM results WHERE test_id = ? AND is_ranked""", (rs, i) -> new ScoreStats(rs.getLong("n"),
                round(rs.getBigDecimal("avg")), round(rs.getBigDecimal("median")), rs.getBigDecimal("hi"),
                rs.getBigDecimal("lo"), round(rs.getBigDecimal("sd")), round(rs.getBigDecimal("acc")),
                rs.getObject("t") == null ? null : ((Number) rs.getObject("t")).intValue()), testId);

        List<Bucket> distribution = distribution(testId, max);

        long evaluated = byStatus.getOrDefault("EVALUATED", 0L);
        Map<UUID, long[]> perQuestion = new HashMap<>();
        Map<UUID, Integer> avgTime = new HashMap<>();
        jdbc.query("""
                SELECT aa.question_id,
                       count(*) FILTER (WHERE aa.state IN ('ANSWERED', 'ANSWERED_AND_MARKED')) AS attempted,
                       count(*) FILTER (WHERE aa.outcome = 'CORRECT') AS correct,
                       count(*) FILTER (WHERE aa.outcome = 'INCORRECT') AS incorrect,
                       count(*) FILTER (WHERE aa.outcome = 'PARTIAL') AS partial,
                       avg(aa.time_spent_seconds) FILTER (WHERE aa.state IN ('ANSWERED', 'ANSWERED_AND_MARKED')) AS t
                FROM attempt_answers aa JOIN attempts a ON a.id = aa.attempt_id
                WHERE a.test_id = ? AND a.status = 'EVALUATED'
                GROUP BY aa.question_id""", rs -> {
            UUID q = rs.getObject("question_id", UUID.class);
            perQuestion.put(q, new long[]{rs.getLong("attempted"), rs.getLong("correct"), rs.getLong("incorrect"),
                    rs.getLong("partial")});
            Object t = rs.getObject("t");
            avgTime.put(q, t == null ? null : ((Number) t).intValue());
        }, testId);

        Map<UUID, String> sectionNames = structure.sections().stream()
                .collect(Collectors.toMap(SectionSpec::id, SectionSpec::name));
        Map<UUID, Integer> sectionOrder = structure.sections().stream()
                .collect(Collectors.toMap(SectionSpec::id, SectionSpec::displayOrder));
        List<QuestionSlot> slots = structure.slots().stream()
                .sorted(Comparator.comparing((QuestionSlot s) -> sectionOrder.getOrDefault(s.sectionId(), 0))
                        .thenComparingInt(QuestionSlot::displayOrder))
                .toList();
        Map<UUID, QuestionSummaryDto> summaries = questions.findSummaries(
                slots.stream().map(QuestionSlot::questionId).toList());

        List<QuestionStat> qs = new ArrayList<>();
        int number = 0;
        for (QuestionSlot slot : slots) {
            long[] c = perQuestion.getOrDefault(slot.questionId(), new long[4]);
            BigDecimal attemptRate = evaluated == 0 ? null : ratio(c[0], evaluated);
            BigDecimal accuracy = c[0] == 0 ? null : ratio(c[1], c[0]);
            qs.add(new QuestionStat(++number, slot.questionId(), sectionNames.get(slot.sectionId()),
                    summaries.get(slot.questionId()), c[0], attemptRate, c[1], c[2], c[3], accuracy,
                    avgTime.get(slot.questionId()), flag(attemptRate, accuracy, c[0])));
        }
        return new TestStatsDto(testId, structure.test().title(), max, byStatus, scores, distribution, qs);
    }

    /** 10 equal-width bands from 0 to max. Negative scores fall into the first band, and max into the last. */
    private List<Bucket> distribution(UUID testId, BigDecimal max) {
        long[] counts = new long[BUCKETS];
        if (max != null && max.signum() > 0) {
            jdbc.query("""
                    SELECT LEAST(GREATEST(width_bucket(score, 0, ?, ?), 1), ?) AS b, count(*) AS n
                    FROM results WHERE test_id = ? AND is_ranked GROUP BY 1""", rs -> {
                counts[rs.getInt("b") - 1] = rs.getLong("n");
            }, max, BUCKETS, BUCKETS, testId);
        }
        List<Bucket> buckets = new ArrayList<>();
        BigDecimal width = max == null || max.signum() == 0 ? BigDecimal.ONE
                : max.divide(BigDecimal.valueOf(BUCKETS), 2, RoundingMode.HALF_UP);
        for (int i = 0; i < BUCKETS; i++) {
            buckets.add(new Bucket(width.multiply(BigDecimal.valueOf(i)), width.multiply(BigDecimal.valueOf(i + 1)),
                    counts[i]));
        }
        return buckets;
    }

    static String flag(BigDecimal attemptRate, BigDecimal accuracy, long attempted) {
        if (attemptRate != null && attemptRate.compareTo(BigDecimal.valueOf(30)) < 0) {
            return "SKIPPED";
        }
        if (accuracy == null || attempted < 5) {
            return null;   // too little data to judge
        }
        if (accuracy.compareTo(BigDecimal.valueOf(20)) < 0) {
            return "TOO_HARD";
        }
        return accuracy.compareTo(BigDecimal.valueOf(90)) > 0 ? "TOO_EASY" : null;
    }

    private static BigDecimal ratio(long part, long whole) {
        return BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal round(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }
}
