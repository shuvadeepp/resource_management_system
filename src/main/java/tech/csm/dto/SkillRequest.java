package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST / PUT /api/skills. PUT poora replace hai (skill_name hamesha chahiye).
 * skill_category optional hai - same category wale skills "related" maane jaate hain (matching me).
 */
public record SkillRequest(

        @NotBlank(message = "skill_name is required")
        @Size(max = 100, message = "skill_name must be at most 100 characters")
        @JsonProperty("skill_name") String skillName,

        @Size(max = 100, message = "skill_category must be at most 100 characters")
        @JsonProperty("skill_category") String skillCategory
) {}
