package com.examprep.question.workflow;

import com.examprep.question.workflow.StudioDtos.ReviewerDto;
import com.examprep.rbac.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who may review questions of a subject: active staff holding {@code question.review}
 * whose subject scope (if any) includes the subject. Auto-assignment picks the eligible
 * reviewer with the fewest open reviews, ignoring super-admin-style "all permissions"
 * roles so that reviews land with the people whose job it is.
 */
@Component
@RequiredArgsConstructor
public class ReviewerDirectory {

    static final String REVIEW = "question.review";

    private final JdbcTemplate jdbc;
    private final PermissionService permissions;

    private record Candidate(UUID id, String name, String email, Set<String> roles, long openReviews) {
    }

    /** Eligible reviewers for a subject, least loaded first. {@code exclude} (the submitter) is left out. */
    public List<ReviewerDto> eligible(UUID subjectId, UUID exclude) {
        return candidates().stream()
                .filter(c -> !c.id().equals(exclude))
                .filter(c -> canReview(c.roles(), c.id(), subjectId))
                .sorted(Comparator.comparingLong(Candidate::openReviews).thenComparing(Candidate::name))
                .map(c -> new ReviewerDto(c.id(), c.name(), c.email(), c.openReviews()))
                .toList();
    }

    public boolean isEligible(UUID userId, UUID subjectId) {
        return candidates().stream().anyMatch(c -> c.id().equals(userId) && canReview(c.roles(), c.id(), subjectId));
    }

    /** The least-loaded dedicated reviewer, if any. */
    public Optional<ReviewerDto> pickFor(UUID subjectId, UUID exclude) {
        return candidates().stream()
                .filter(c -> !c.id().equals(exclude))
                .filter(c -> c.roles().stream().noneMatch(permissions::isAllPermissionsRole))
                .filter(c -> canReview(c.roles(), c.id(), subjectId))
                .min(Comparator.comparingLong(Candidate::openReviews).thenComparing(Candidate::name))
                .map(c -> new ReviewerDto(c.id(), c.name(), c.email(), c.openReviews()));
    }

    private boolean canReview(Set<String> roles, UUID userId, UUID subjectId) {
        if (!permissions.permissionsOf(roles).contains(REVIEW)) {
            return false;
        }
        return permissions.subjectScope(roles, userId).map(s -> s.contains(subjectId)).orElse(true);
    }

    private List<Candidate> candidates() {
        return jdbc.query("""
                select u.id, u.full_name, u.email, array_agg(r.name) as roles,
                       (select count(*) from questions q where q.reviewer_id = u.id and q.status = 'IN_REVIEW') as open
                from users u
                join user_roles ur on ur.user_id = u.id
                join roles r on r.id = ur.role_id
                where u.status = 'ACTIVE' and r.staff
                group by u.id, u.full_name, u.email
                """, (rs, i) -> new Candidate(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                roles(rs.getArray(4)), rs.getLong(5)));
    }

    private static Set<String> roles(Array array) throws SQLException {
        return array == null ? Set.of() : Set.copyOf(Arrays.asList((String[]) array.getArray()));
    }
}
