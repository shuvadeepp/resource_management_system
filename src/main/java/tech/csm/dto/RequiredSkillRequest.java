package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RequiredSkillRequest {

    @NotBlank(message = "skill_name must not be empty")
    private final String skillName;

    @NotNull(message = "mandatory must be true or false")
    private final Boolean mandatory;

    @NotNull(message = "minimum_proficiency is required")
    @Min(value = 1, message = "minimum_proficiency must be 1 to 5")
    @Max(value = 5, message = "minimum_proficiency must be 1 to 5")
    private final Integer minimumProficiency;

    @JsonCreator
    public RequiredSkillRequest(
            @JsonProperty("skill_name") String skillName,
            @JsonProperty("mandatory") Boolean mandatory,
            @JsonProperty("minimum_proficiency") Integer minimumProficiency) {
        this.skillName = skillName;
        this.mandatory = mandatory;
        this.minimumProficiency = minimumProficiency;
    }

    @JsonProperty("skill_name")
    public String getSkillName() { return skillName; }

    @JsonProperty("mandatory")
    public Boolean getMandatory() { return mandatory; }

    @JsonProperty("minimum_proficiency")
    public Integer getMinimumProficiency() { return minimumProficiency; }
}