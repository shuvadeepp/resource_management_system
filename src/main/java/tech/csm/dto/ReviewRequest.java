package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/** PUT /api/learning-recommendations/{id}/review - APPROVED | REJECTED | IN_PROGRESS | COMPLETED */
public record ReviewRequest(

        @NotBlank(message = "review_status is required")
        @JsonProperty("review_status") String reviewStatus,

        @JsonProperty("review_comments") String reviewComments
) {}
