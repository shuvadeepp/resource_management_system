package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EmployeeSkillProfile {

    private final Integer skillId;
    private final String skillName;
    private final String proficiencyLevel;
    private final Integer experienceMonths;
    private final String validationStatus;

    @JsonCreator
    public EmployeeSkillProfile(
            @JsonProperty("skill_id") Integer skillId,
            @JsonProperty("skill_name") String skillName,
            @JsonProperty("proficiency_level") String proficiencyLevel,
            @JsonProperty("experience_months") Integer experienceMonths,
            @JsonProperty("validation_status") String validationStatus) {
        this.skillId = skillId;
        this.skillName = skillName;
        this.proficiencyLevel = proficiencyLevel;
        this.experienceMonths = experienceMonths;
        this.validationStatus = validationStatus;
    }

    @JsonProperty("skill_id")
    public Integer getSkillId() { return skillId; }

    @JsonProperty("skill_name")
    public String getSkillName() { return skillName; }

    @JsonProperty("proficiency_level")
    public String getProficiencyLevel() { return proficiencyLevel; }

    @JsonProperty("experience_months")
    public Integer getExperienceMonths() { return experienceMonths; }

    @JsonProperty("validation_status")
    public String getValidationStatus() { return validationStatus; }
}