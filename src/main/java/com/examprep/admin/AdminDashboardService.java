package com.examprep.admin;

import com.examprep.admin.AdminDtos.DailyPoint;
import com.examprep.admin.AdminDtos.DashboardDto;
import com.examprep.admin.AdminDtos.Revenue;
import com.examprep.admin.AdminDtos.SeriesRevenue;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin dashboard read model. It is plain aggregate SQL over the operational tables
 * (the admin module is a reporting layer that sits above the others). "Today" and
 * "daily" are computed in IST, the business timezone. At much larger volumes, move this
 * to a read replica or materialised views.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DashboardDto dashboard() {
        Instant now = Instant.now(clock);
        Timestamp startOfToday = Timestamp.from(LocalDate.now(clock.withZone(BUSINESS_ZONE))
                .atStartOfDay(BUSINESS_ZONE).toInstant());
        Timestamp days30 = Timestamp.from(now.minus(30, ChronoUnit.DAYS));
        Timestamp days7 = Timestamp.from(now.minus(7, ChronoUnit.DAYS));

        Map<String, Long> usersByRole = counts(
                "SELECT r.name, count(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id GROUP BY r.name");
        Long newStudents = jdbc.queryForObject("""
                SELECT count(*) FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
                WHERE r.name = 'STUDENT' AND u.created_at >= ?""", Long.class, days7);
        Map<String, Long> testsByStatus = counts("SELECT status, count(*) FROM tests GROUP BY status");

        Map<String, Object> attempts = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE status = 'IN_PROGRESS') AS live,
                       count(*) FILTER (WHERE started_at >= ?) AS today,
                       count(*) AS total
                FROM attempts""", startOfToday);
        List<DailyPoint> attemptsDaily = jdbc.query("""
                SELECT (started_at AT TIME ZONE 'Asia/Kolkata')::date AS d, count(*) AS n
                FROM attempts WHERE started_at >= ? GROUP BY 1 ORDER BY 1""",
                (rs, i) -> new DailyPoint(rs.getObject("d", LocalDate.class), rs.getLong("n"), null), days30);

        Map<String, Object> money = jdbc.queryForMap("""
                SELECT coalesce(sum(amount), 0) AS total,
                       coalesce(sum(amount) FILTER (WHERE paid_at >= ?), 0) AS last30,
                       coalesce(sum(amount) FILTER (WHERE paid_at >= ?), 0) AS today,
                       count(*) AS paid
                FROM payments WHERE status = 'PAID'""", days30, startOfToday);
        List<DailyPoint> revenueDaily = jdbc.query("""
                SELECT (paid_at AT TIME ZONE 'Asia/Kolkata')::date AS d, count(*) AS n, sum(amount) AS amt
                FROM payments WHERE status = 'PAID' AND paid_at >= ? GROUP BY 1 ORDER BY 1""",
                (rs, i) -> new DailyPoint(rs.getObject("d", LocalDate.class), rs.getLong("n"),
                        rs.getBigDecimal("amt")), days30);
        List<SeriesRevenue> topSeries = jdbc.query("""
                SELECT s.id, s.name, sum(p.amount) AS revenue, count(*) AS n
                FROM payments p JOIN test_series s ON s.id = p.series_id
                WHERE p.status = 'PAID' GROUP BY s.id, s.name ORDER BY revenue DESC LIMIT 5""",
                (rs, i) -> new SeriesRevenue(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getBigDecimal("revenue"), rs.getLong("n")));

        return new DashboardDto(usersByRole, newStudents == null ? 0 : newStudents, testsByStatus,
                ((Number) attempts.get("live")).longValue(), ((Number) attempts.get("today")).longValue(),
                ((Number) attempts.get("total")).longValue(), attemptsDaily,
                new Revenue((BigDecimal) money.get("total"), (BigDecimal) money.get("last30"),
                        (BigDecimal) money.get("today"), ((Number) money.get("paid")).longValue(), revenueDaily,
                        topSeries));
    }

    private Map<String, Long> counts(String sql) {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            out.put(rs.getString(1), rs.getLong(2));
        });
        return out;
    }
}
