package com.examprep.approval;

import java.math.BigDecimal;

/**
 * Describes a guarded operation.
 *
 * @param amount optional value compared with the policy threshold (e.g. a refund amount);
 *               below the threshold no approval is needed
 */
public record ApprovalSpec(String action, String entityType, Object entityId, String title, Object payload,
                           BigDecimal amount) {

    public static ApprovalSpec of(String action, String entityType, Object entityId, String title, Object payload) {
        return new ApprovalSpec(action, entityType, entityId, title, payload, null);
    }
}
