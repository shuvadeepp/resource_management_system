package tech.csm.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST / PUT /api/learning-resources. PUT poora replace hai (skill_id aur resource_title hamesha chahiye).
 * resource_type: COURSE | CERTIFICATION | DOCUMENTATION | MENTORING | PRACTICAL_ASSIGNMENT | OTHER | ASSESSMENT
 * validation_method: KNOWLEDGE_ASSESSMENT | PRACTICAL_ASSIGNMENT | DEMONSTRATION | TECHNICAL_REVIEW | CERTIFICATE | NONE
 * Approval is request se nahi hoti - alag endpoint (PUT /{id}/approval).
 */
public record LearningResourceRequest(

        @NotNull(message = "skill_id is required")
        @JsonProperty("skill_id") Integer skillId,

        @NotBlank(message = "resource_title is required")
        @Size(max = 250, message = "resource_title must be at most 250 characters")
        @JsonProperty("resource_title") String resourceTitle,

        @JsonProperty("resource_type") String resourceType,

        @Size(max = 150, message = "provider must be at most 150 characters")
        @JsonProperty("provider") String provider,

        @Size(max = 1000, message = "resource_url must be at most 1000 characters")
        @JsonProperty("resource_url") String resourceUrl,

        @JsonProperty("resource_description") String resourceDescription,

        // default 1
        @JsonProperty("from_proficiency") Integer fromProficiency,

        @NotNull(message = "to_proficiency is required")
        @JsonProperty("to_proficiency") Integer toProficiency,

        @JsonProperty("estimated_hours") BigDecimal estimatedHours,

        @JsonProperty("validation_method") String validationMethod,

        // default true
        @JsonProperty("is_active") Boolean isActive
) {}

