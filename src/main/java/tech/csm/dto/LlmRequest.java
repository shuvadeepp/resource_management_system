package tech.csm.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LlmRequest(
        @JsonProperty("requirement") Requirement requirement
) {

    public record Requirement(
            @JsonProperty("projectName") String projectName,
            @JsonProperty("requiredSkills") List<Skill> requiredSkills,
            @JsonProperty("minimumExperienceYears") BigDecimal minimumExperienceYears,
            @JsonProperty("joiningDate") String joiningDate
    ) {}

    public record Skill(
            @JsonProperty("name") String name,
            @JsonProperty("mandatory") Boolean mandatory,
            @JsonProperty("minimumProficiency") Integer minimumProficiency
    ) {}
}