package com.examprep.rbac.service;

import com.examprep.approval.ApprovalAction;
import com.examprep.approval.ApprovalDtos;
import com.examprep.rbac.dto.RbacDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Executes an approved "role.update" request. The update runs as the approver, who therefore
 * must also hold every permission being granted (no escalation through approvals).
 */
@Component
@RequiredArgsConstructor
public class RoleUpdateApproval implements ApprovalAction {

    private final RoleAdminService roles;
    private final ObjectMapper json;

    @Override
    public String action() {
        return "role.update";
    }

    @Override
    public JsonNode execute(ApprovalDtos.ApprovalRequestDto request) throws Exception {
        RbacDtos.UpdateRoleRequest body = json.treeToValue(request.payload().get("request"),
                RbacDtos.UpdateRoleRequest.class);
        RbacDtos.RoleDto updated = roles.update(request.entityId(), body, PermissionChecker.currentUser());
        return json.createObjectNode().put("role", updated.name()).put("permissions", updated.permissions().size());
    }
}
