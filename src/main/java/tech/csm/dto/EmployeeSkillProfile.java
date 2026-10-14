package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmployeeSkillProfile(
        @JsonProperty("skill_id") Integer skillId,
        @JsonProperty("skill_name") String skillName,
        @JsonProperty("proficiency_level") String proficiencyLevel,
        @JsonProperty("experience_months") Integer experienceMonths,
        @JsonProperty("validation_status") String validationStatus
) {}