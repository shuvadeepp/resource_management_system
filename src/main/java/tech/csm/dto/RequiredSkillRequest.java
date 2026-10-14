package tech.csm.dto;
 

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RequiredSkillRequest(

        @NotBlank(message = "skill_name must not be empty")
        @JsonProperty("skill_name")
        String skillName,

        @NotNull(message = "mandatory must be true or false")
        @JsonProperty("mandatory")
        Boolean mandatory,

        @NotNull(message = "minimum_proficiency is required")
        @Min(value = 1, message = "minimum_proficiency must be 1 to 5")
        @Max(value = 5, message = "minimum_proficiency must be 1 to 5")
        @JsonProperty("minimum_proficiency")
        Integer minimumProficiency
) {}