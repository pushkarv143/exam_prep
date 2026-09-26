package com.examprep.test.approval;

import com.examprep.approval.ApprovalAction;
import com.examprep.approval.ApprovalDtos;
import com.examprep.test.dto.TestDtos.TestDto;
import com.examprep.test.service.TestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Executes an approved "test.publish" request (re-validating the paper at approval time). */
@Component
@RequiredArgsConstructor
public class PublishTestApproval implements ApprovalAction {

    private final TestService tests;
    private final ObjectMapper json;

    @Override
    public String action() {
        return "test.publish";
    }

    @Override
    public JsonNode execute(ApprovalDtos.ApprovalRequestDto request) {
        TestDto published = tests.publish(UUID.fromString(request.entityId()));
        return json.createObjectNode().put("status", published.status().name())
                .put("totalQuestions", published.totalQuestions());
    }
}
