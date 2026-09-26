package com.examprep.approval;

import com.examprep.audit.service.AuditContext;
import com.examprep.common.api.PageResponse;
import com.examprep.common.config.AppProperties;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.common.util.Uuids;
import com.examprep.notification.entity.NotificationChannel;
import com.examprep.notification.entity.NotificationTemplate;
import com.examprep.notification.outbox.NotificationOutbox;
import com.examprep.rbac.service.PermissionService;
import com.examprep.security.AuthUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stores, decides and executes maker-checker requests.
 *
 * <p>Rules: the approver must not be the requester, must hold {@code approval.decide} and the
 * policy's approve permission (e.g. {@code test.publish}), and the request must still be
 * PENDING and unexpired. The state change is a conditional UPDATE, so two approvers
 * clicking at once cannot both execute it.
 */
@Slf4j
@Service
public class ApprovalService {

    private static final String SELECT = """
            select a.*, p.description as policy_description, p.approve_permission,
                   rq.full_name as requested_by_name, dc.full_name as decided_by_name
            from approval_requests a
            join approval_policies p on p.action = a.action
            join users rq on rq.id = a.requested_by
            left join users dc on dc.id = a.decided_by
            """;
    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a z").withZone(ZoneId.of("Asia/Kolkata"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PermissionService permissions;
    private final NotificationOutbox notifications;
    private final AppProperties appProperties;
    private final Clock clock;
    private final TransactionTemplate newTx;
    /** Resolved lazily: executors depend on services that depend on the guard, which depends on us. */
    private final ObjectProvider<ApprovalAction> actionProvider;
    private volatile Map<String, ApprovalAction> actions;

    public ApprovalService(JdbcTemplate jdbc, ObjectMapper json, PermissionService permissions,
                           NotificationOutbox notifications, AppProperties appProperties, Clock clock,
                           PlatformTransactionManager txManager, ObjectProvider<ApprovalAction> actionProvider) {
        this.jdbc = jdbc;
        this.json = json;
        this.permissions = permissions;
        this.notifications = notifications;
        this.appProperties = appProperties;
        this.clock = clock;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.actionProvider = actionProvider;
    }

    // ------------------------------------------------------------------ policies

    public record Policy(String action, boolean enabled, BigDecimal threshold, String approvePermission,
                         int expiryHours, String description, Instant updatedAt) {
    }

    public Optional<Policy> policy(String action) {
        return jdbc.query("select * from approval_policies where action = ?", policyMapper(), action)
                .stream().findFirst();
    }

    public List<ApprovalDtos.PolicyDto> policies() {
        return jdbc.query("select * from approval_policies order by action", policyMapper()).stream()
                .map(p -> new ApprovalDtos.PolicyDto(p.action(), p.enabled(), p.threshold(), p.approvePermission(),
                        p.expiryHours(), p.description(), p.updatedAt()))
                .toList();
    }

    public ApprovalDtos.PolicyDto updatePolicy(String action, ApprovalDtos.UpdatePolicyRequest req, AuthUser actor) {
        Policy before = policy(action).orElseThrow(() -> NotFoundException.of("Approval policy", action));
        jdbc.update("""
                update approval_policies set enabled = ?, threshold = ?, expiry_hours = ?, updated_by = ?,
                       updated_at = now()
                where action = ?
                """, req.enabled(), req.threshold(), req.expiryHours(), actor.id(), action);
        ApprovalDtos.PolicyDto after = policies().stream().filter(p -> p.action().equals(action)).findFirst()
                .orElseThrow();
        AuditContext.action("approval.policy.update");
        AuditContext.entity("APPROVAL_POLICY", action);
        AuditContext.change(before, after);
        return after;
    }

    // ------------------------------------------------------------------ requests

    /**
     * Stores a new PENDING request in its own transaction (it must survive the rollback of
     * the guarded operation). If an open request already exists for the same action and
     * entity, that one is returned.
     */
    ApprovalRequiredException createRequest(ApprovalSpec spec, Policy policy, AuthUser requester, String reason) {
        UUID id = Uuids.v7();
        String entityId = spec.entityId().toString();
        Instant expires = clock.instant().plusSeconds(policy.expiryHours() * 3600L);
        try {
            newTx.executeWithoutResult(s -> jdbc.update("""
                    insert into approval_requests (id, action, entity_type, entity_id, title, payload, reason,
                        requested_by, expires_at)
                    values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    """, id, spec.action(), spec.entityType(), entityId, cut(spec.title(), 255),
                    toJson(spec.payload()), reason, requester.id(), Timestamp.from(expires)));
        } catch (DuplicateKeyException e) {
            UUID existing = jdbc.queryForObject("select id from approval_requests where action = ? and entity_id = ? "
                    + "and status = 'PENDING'", UUID.class, spec.action(), entityId);
            return new ApprovalRequiredException(get(existing, requester), true);
        }
        ApprovalDtos.ApprovalRequestDto dto = get(id, requester);
        newTx.executeWithoutResult(s -> notifyApprovers(dto, policy, requester));
        AuditContext.action(spec.action() + ".requested");
        AuditContext.entity(spec.entityType(), entityId);
        AuditContext.meta("approvalRequestId", id);
        log.info("Approval requested: {} {}:{} by {}", spec.action(), spec.entityType(), entityId, requester.id());
        return new ApprovalRequiredException(dto, false);
    }

    public ApprovalDtos.ApprovalRequestDto get(UUID id, AuthUser viewer) {
        return jdbc.query(SELECT + " where a.id = ?", mapper(viewer), id).stream().findFirst()
                .orElseThrow(() -> NotFoundException.of("Approval request", id));
    }

    /**
     * @param view "to-decide" (pending, not mine, and I may approve), "mine" (I requested),
     *             or anything else for all requests
     */
    public PageResponse<ApprovalDtos.ApprovalRequestDto> search(AuthUser viewer, String view,
                                                                ApprovalDtos.Status status, int page, int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where 1=1");
        if ("to-decide".equals(view)) {
            List<String> allowed = decidableActions(viewer);
            if (allowed.isEmpty()) {
                return new PageResponse<>(List.of(), page, size, 0, 0, true);
            }
            where.append(" and a.status = 'PENDING' and a.expires_at > now() and a.requested_by <> ? "
                    + "and a.action = any (?)");
            args.add(viewer.id());
            args.add(allowed.toArray(new String[0]));
        } else if ("mine".equals(view)) {
            where.append(" and a.requested_by = ?");
            args.add(viewer.id());
        }
        if (status != null) {
            where.append(" and a.status = ?");
            args.add(status.name());
        }
        Long total = jdbc.queryForObject("select count(*) from approval_requests a" + where, Long.class,
                args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<ApprovalDtos.ApprovalRequestDto> rows = jdbc.query(
                SELECT + where + " order by a.requested_at desc limit ? offset ?", mapper(viewer), pageArgs.toArray());
        long t = total == null ? 0 : total;
        int pages = (int) Math.ceil(t / (double) size);
        return new PageResponse<>(rows, page, size, t, pages, page >= pages - 1);
    }

    public ApprovalDtos.ApprovalSummary summary(AuthUser viewer) {
        List<String> allowed = decidableActions(viewer);
        long waiting = allowed.isEmpty() ? 0 : count("""
                select count(*) from approval_requests
                where status = 'PENDING' and expires_at > now() and requested_by <> ? and action = any (?)
                """, viewer.id(), allowed.toArray(new String[0]));
        long mine = count("select count(*) from approval_requests where status = 'PENDING' and requested_by = ?",
                viewer.id());
        return new ApprovalDtos.ApprovalSummary(waiting, mine);
    }

    // ------------------------------------------------------------------ decisions

    public ApprovalDtos.ApprovalRequestDto approve(UUID id, AuthUser approver, String comment) {
        ApprovalDtos.ApprovalRequestDto req = get(id, approver);
        assertCanDecide(req, approver);
        int n = jdbc.update("""
                update approval_requests set status = 'APPROVED', decided_by = ?, decided_at = now(),
                       decision_comment = ?, version = version + 1
                where id = ? and status = 'PENDING' and expires_at > now() and requested_by <> ?
                """, approver.id(), comment, id, approver.id());
        if (n == 0) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_PENDING);
        }
        audit("approval.approve", req);

        ApprovalAction action = actions().get(req.action());
        try {
            if (action == null) {
                throw new IllegalStateException("No executor registered for " + req.action());
            }
            JsonNode result = ApprovalExecution.run(req.action(), req.entityId(), () -> action.execute(req));
            jdbc.update("update approval_requests set status = 'EXECUTED', executed_at = now(), result = ?::jsonb "
                    + "where id = ?", result == null ? null : result.toString(), id);
            log.info("Approval {} ({}) executed by {}", id, req.action(), approver.id());
        } catch (Exception e) {
            String message = e instanceof BusinessException be ? be.getMessage() : e.getClass().getSimpleName()
                    + ": " + e.getMessage();
            jdbc.update("update approval_requests set status = 'FAILED', error = ? where id = ?", cut(message, 4000), id);
            log.warn("Approval {} ({}) approved but execution failed: {}", id, req.action(), message);
        }
        ApprovalDtos.ApprovalRequestDto done = get(id, approver);
        notifyRequester(done, "approved", approver);
        return done;
    }

    public ApprovalDtos.ApprovalRequestDto reject(UUID id, AuthUser approver, String comment) {
        ApprovalDtos.ApprovalRequestDto req = get(id, approver);
        assertCanDecide(req, approver);
        int n = jdbc.update("""
                update approval_requests set status = 'REJECTED', decided_by = ?, decided_at = now(),
                       decision_comment = ?, version = version + 1
                where id = ? and status = 'PENDING' and requested_by <> ?
                """, approver.id(), comment, id, approver.id());
        if (n == 0) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_PENDING);
        }
        audit("approval.reject", req);
        ApprovalDtos.ApprovalRequestDto done = get(id, approver);
        notifyRequester(done, "rejected", approver);
        return done;
    }

    public ApprovalDtos.ApprovalRequestDto cancel(UUID id, AuthUser requester) {
        ApprovalDtos.ApprovalRequestDto req = get(id, requester);
        if (!req.requestedBy().equals(requester.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the requester can cancel a request");
        }
        int n = jdbc.update("update approval_requests set status = 'CANCELLED', decided_at = now(), "
                + "version = version + 1 where id = ? and status = 'PENDING'", id);
        if (n == 0) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_PENDING);
        }
        audit("approval.cancel", req);
        return get(id, requester);
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
    public void expireOld() {
        int n = jdbc.update("update approval_requests set status = 'EXPIRED', version = version + 1 "
                + "where status = 'PENDING' and expires_at <= now()");
        if (n > 0) {
            log.info("Expired {} approval requests", n);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, ApprovalAction> actions() {
        Map<String, ApprovalAction> a = actions;
        if (a == null) {
            a = actionProvider.orderedStream()
                    .collect(Collectors.toMap(ApprovalAction::action, Function.identity()));
            actions = a;
        }
        return a;
    }

    private void assertCanDecide(ApprovalDtos.ApprovalRequestDto req, AuthUser approver) {
        if (req.status() != ApprovalDtos.Status.PENDING || req.expiresAt().isBefore(clock.instant())) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_PENDING);
        }
        if (req.requestedBy().equals(approver.id())) {
            throw new BusinessException(ErrorCode.APPROVAL_SELF_DECISION);
        }
        String required = policy(req.action()).map(Policy::approvePermission).orElse(req.action());
        if (!permissions.has(approver, "approval.decide") || !permissions.has(approver, required)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Approving this needs the permissions approval.decide and " + required);
        }
    }

    private List<String> decidableActions(AuthUser viewer) {
        if (!permissions.has(viewer, "approval.decide")) {
            return List.of();
        }
        return jdbc.query("select action, approve_permission from approval_policies",
                        (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}).stream()
                .filter(p -> permissions.has(viewer, p[1]))
                .map(p -> p[0])
                .toList();
    }

    private void notifyApprovers(ApprovalDtos.ApprovalRequestDto req, Policy policy, AuthUser requester) {
        Set<String> roles = jdbc.queryForList("select name from roles where staff", String.class).stream()
                .filter(r -> {
                    Set<String> p = permissions.permissionsOfRole(r);
                    return p.contains("approval.decide") && p.contains(policy.approvePermission());
                })
                .collect(Collectors.toSet());
        if (roles.isEmpty()) {
            return;
        }
        List<Map<String, Object>> approvers = jdbc.queryForList("""
                select distinct u.id, u.email, u.full_name from users u
                join user_roles ur on ur.user_id = u.id join roles r on r.id = ur.role_id
                where r.name = any (?) and u.status = 'ACTIVE' and u.id <> ?
                limit 50
                """, roles.toArray(new String[0]), requester.id());
        for (Map<String, Object> a : approvers) {
            notifications.enqueue((UUID) a.get("id"), NotificationChannel.EMAIL, NotificationTemplate.APPROVAL_REQUESTED,
                    (String) a.get("email"), Map.of(
                            "name", a.get("full_name"),
                            "requester", req.requestedByName(),
                            "title", req.title(),
                            "reason", req.reason() == null ? "-" : req.reason(),
                            "approvalUrl", appProperties.frontendUrl() + "/admin/approvals/" + req.id(),
                            "expiresAt", EXPIRY_FORMAT.format(req.expiresAt())));
        }
    }

    private void notifyRequester(ApprovalDtos.ApprovalRequestDto req, String decision, AuthUser decider) {
        try {
            Map<String, Object> user = jdbc.queryForMap("select email, full_name from users where id = ?",
                    req.requestedBy());
            newTx.executeWithoutResult(s -> notifications.enqueue(req.requestedBy(), NotificationChannel.EMAIL,
                    NotificationTemplate.APPROVAL_DECIDED, (String) user.get("email"), Map.of(
                            "name", user.get("full_name"),
                            "title", req.title(),
                            "decision", req.status() == ApprovalDtos.Status.FAILED ? "approved (but execution failed)"
                                    : decision,
                            "decider", req.decidedByName() == null ? decider.email() : req.decidedByName(),
                            "comment", req.decisionComment() == null ? "-" : req.decisionComment(),
                            "approvalUrl", appProperties.frontendUrl() + "/admin/approvals/" + req.id())));
        } catch (RuntimeException e) {
            log.warn("Could not notify requester of approval {}: {}", req.id(), e.getMessage());
        }
    }

    private void audit(String action, ApprovalDtos.ApprovalRequestDto req) {
        AuditContext.action(action);
        AuditContext.entity("APPROVAL_REQUEST", req.id());
        AuditContext.meta("approvalAction", req.action());
        AuditContext.meta("target", req.entityType() + ":" + req.entityId());
        AuditContext.meta("requestedBy", req.requestedBy());
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private RowMapper<Policy> policyMapper() {
        return (rs, i) -> new Policy(rs.getString("action"), rs.getBoolean("enabled"), rs.getBigDecimal("threshold"),
                rs.getString("approve_permission"), rs.getInt("expiry_hours"), rs.getString("description"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private RowMapper<ApprovalDtos.ApprovalRequestDto> mapper(AuthUser viewer) {
        Instant now = clock.instant();
        return (rs, i) -> {
            ApprovalDtos.Status status = ApprovalDtos.Status.valueOf(rs.getString("status"));
            UUID requestedBy = rs.getObject("requested_by", UUID.class);
            Instant expiresAt = instant(rs, "expires_at");
            boolean mine = viewer != null && viewer.id().equals(requestedBy);
            boolean pending = status == ApprovalDtos.Status.PENDING && expiresAt.isAfter(now);
            boolean canDecide = pending && !mine && viewer != null && permissions.has(viewer, "approval.decide")
                    && permissions.has(viewer, rs.getString("approve_permission"));
            return new ApprovalDtos.ApprovalRequestDto(
                    rs.getObject("id", UUID.class), rs.getString("action"), rs.getString("policy_description"),
                    rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("title"),
                    read(rs.getString("payload")), rs.getString("reason"), status, requestedBy,
                    rs.getString("requested_by_name"), instant(rs, "requested_at"), expiresAt,
                    rs.getObject("decided_by", UUID.class), rs.getString("decided_by_name"), instant(rs, "decided_at"),
                    rs.getString("decision_comment"), instant(rs, "executed_at"), read(rs.getString("result")),
                    rs.getString("error"), canDecide, pending && mine);
        };
    }

    private JsonNode read(String s) {
        if (s == null) {
            return null;
        }
        try {
            return json.readTree(s);
        } catch (IOException e) {
            return null;
        }
    }

    private String toJson(Object o) {
        try {
            return o == null ? "{}" : json.writeValueAsString(o);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
