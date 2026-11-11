package tech.csm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/learning-recommendations/{id}/evidence (FR-GAP-07) - training ya assessment ke baad evidence.
 * evidence_type: ASSESSMENT | CERTIFICATION | PROJECT_EXPERIENCE | PRACTICAL_ASSIGNMENT | OTHER
 * new_proficiency (1-5) do to employee ki skill rating update hoti hai (skill pehle se na ho to nayi row banti hai).
 * mark_completed (default true): recommendation COMPLETED ho jaati hai.
 */
public record EvidenceRequest(

        @NotBlank(message = "evidence_type is required")
        @JsonProperty("evidence_type") String evidenceType,

        @NotBlank(message = "evidence_title is required")
        @Size(max = 250, message = "evidence_title must be at most 250 characters")
        @JsonProperty("evidence_title") String evidenceTitle,

        @JsonProperty("evidence_description") String evidenceDescription,

        @JsonProperty("score") BigDecimal score,

        @JsonProperty("maximum_score") BigDecimal maximumScore,

        // default: aaj
        @JsonProperty("evidence_date") LocalDate evidenceDate,

        @JsonProperty("valid_until") LocalDate validUntil,

        @Size(max = 1000, message = "file_reference must be at most 1000 characters")
        @JsonProperty("file_reference") String fileReference,

        @JsonProperty("new_proficiency") Integer newProficiency,

        @JsonProperty("mark_completed") Boolean markCompleted
) {}
