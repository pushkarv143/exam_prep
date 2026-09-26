package com.examprep.approval;

import com.examprep.audit.web.AuditFilter;
import com.examprep.rbac.service.PermissionChecker;
import com.examprep.security.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * The maker-checker guard. Call it at the start of a sensitive operation:
 * <pre>
 * makerChecker.guard(ApprovalSpec.of("test.publish", "TEST", id, "Publish test X", Map.of("testId", id)));
 * </pre>
 * It returns normally (the operation proceeds) when:
 * <ul>
 *   <li>the action's policy is disabled, or the amount is below its threshold,</li>
 *   <li>no user is logged in (system jobs such as the ranking scheduler are never blocked), or</li>
 *   <li>the call is the execution of an already approved request.</li>
 * </ul>
 * Otherwise it stores a PENDING request and throws {@link ApprovalRequiredException} (HTTP 202).
 */
@Component
public class MakerChecker {

    private final ApprovalService approvals;
    /** Global switch (app.approvals.enabled). Off only in tests that exercise other flows. */
    private final boolean enabled;

    public MakerChecker(ApprovalService approvals, @Value("${app.approvals.enabled:true}") boolean enabled) {
        this.approvals = approvals;
        this.enabled = enabled;
    }

    public void guard(ApprovalSpec spec) {
        if (!enabled || ApprovalExecution.isExecuting(spec.action(), spec.entityId())) {
            return;
        }
        AuthUser actor = PermissionChecker.currentUser();
        if (actor == null) {
            return;
        }
        ApprovalService.Policy policy = approvals.policy(spec.action()).orElse(null);
        if (policy == null || !policy.enabled()) {
            return;
        }
        if (policy.threshold() != null && spec.amount() != null && spec.amount().compareTo(policy.threshold()) < 0) {
            return;
        }
        throw approvals.createRequest(spec, policy, actor, currentReason());
    }

    private static String currentReason() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String v = attrs.getRequest().getHeader(AuditFilter.REASON_HEADER);
            return v == null || v.isBlank() ? null : URLDecoder.decode(v, StandardCharsets.UTF_8).trim();
        }
        return null;
    }
}
