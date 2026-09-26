package com.examprep.approval;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Executes an approved request. Implement one bean per guarded action, in the module that
 * owns the operation. The approval module only sees this interface. {@link #execute} runs
 * as the approver, with the maker-checker guard bypassed for exactly this action and entity.
 */
public interface ApprovalAction {

    String action();

    /** Performs the operation. The returned JSON is stored as the request's result. */
    JsonNode execute(ApprovalDtos.ApprovalRequestDto request) throws Exception;
}
