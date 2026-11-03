package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/tagged-people/release body. allocation_id YA decision_id - dono me se exactly ek.
 */
public record ReleaseRequest(

        @JsonProperty("allocation_id") Integer allocationId,

        @JsonProperty("decision_id") Integer decisionId,

        @NotBlank(message = "reason is required: write why this person is being released")
        @JsonProperty("reason") String reason
) {}
