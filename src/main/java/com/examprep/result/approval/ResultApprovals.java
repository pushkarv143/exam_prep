package com.examprep.result.approval;

import com.examprep.approval.ApprovalAction;
import com.examprep.approval.ApprovalDtos;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.result.service.EvaluationService;
import com.examprep.result.service.RankingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/** Executors for the result module's guarded actions. */
@Configuration
@RequiredArgsConstructor
public class ResultApprovals {

    private final ObjectMapper json;

    @Bean
    ApprovalAction finalizeRanksApproval(RankingService ranking) {
        return new ApprovalAction() {
            @Override
            public String action() {
                return "result.finalize";
            }

            @Override
            public JsonNode execute(ApprovalDtos.ApprovalRequestDto request) {
                int ranked = ranking.finalizeRanks(UUID.fromString(request.entityId()));
                if (ranked < 0) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Ranking is already running for this test");
                }
                return json.createObjectNode().put("rankedCandidates", ranked);
            }
        };
    }

    @Bean
    ApprovalAction reEvaluateApproval(EvaluationService evaluation) {
        return new ApprovalAction() {
            @Override
            public String action() {
                return "result.regenerate";
            }

            @Override
            public JsonNode execute(ApprovalDtos.ApprovalRequestDto request) {
                return json.createObjectNode().put("evaluated",
                        evaluation.evaluate(UUID.fromString(request.entityId()), true));
            }
        };
    }
}
