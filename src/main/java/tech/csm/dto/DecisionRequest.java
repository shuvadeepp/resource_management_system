package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/decisions body. decision_maker request body me nahi aata -
 * logged-in user se (JWT -> username -> app_users.user_id) resolve hota hai.
 */
public record DecisionRequest(

        @NotNull(message = "matching_result_id is required")
        @JsonProperty("matching_result_id") Integer matchingResultId,

        @NotNull(message = "requirement_id is required")
        @JsonProperty("requirement_id") Integer requirementId,

        // SELECTED | REJECTED | ON_HOLD | DEFERRED | WITHDRAWN
        @NotBlank(message = "decision is required")
        @JsonProperty("decision") String decision,

        @NotBlank(message = "decision_reason is required: write a short comment for this decision")
        @JsonProperty("decision_reason") String decisionReason
) {}
