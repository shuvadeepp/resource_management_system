package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/** PUT /api/matching-results/{id}/recommendation - RECOMMENDED | NOT_RECOMMENDED | REVIEW_REQUIRED */
public record RecommendationRequest(

        @NotBlank(message = "recommendation_status is required")
        @JsonProperty("recommendation_status") String recommendationStatus
) {}
