package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RequiredSkillRequest(

        @NotBlank(message = "skill_name must not be empty")
        @JsonProperty("skill_name")
        String skillName,

        @JsonProperty("mandatory")
        Boolean mandatory,

        @Min(value = 1, message = "minimum_proficiency must be 1 to 5")
        @Max(value = 5, message = "minimum_proficiency must be 1 to 5")
        @JsonProperty("minimum_proficiency")
        Integer minimumProficiency
) {}