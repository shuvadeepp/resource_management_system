package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/decisions body. decision_maker request body me nahi aata -
 * logged-in user se (JWT -> username -> app_users.user_id) resolve hota hai.
 */
public record DecisionRequest(

        @NotNull @JsonProperty("matching_result_id") Integer matchingResultId,
        @NotNull @JsonProperty("requirement_id") Integer requirementId,

        // SELECTED | REJECTED | ON_HOLD | DEFERRED | WITHDRAWN
        @NotBlank @JsonProperty("decision") String decision,

        @NotBlank @JsonProperty("decision_reason") String decisionReason
) {}
