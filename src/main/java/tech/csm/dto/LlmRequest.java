package tech.csm.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LlmRequest {

    private final Requirement requirement;

    @JsonCreator
    public LlmRequest(@JsonProperty("requirement") Requirement requirement) {
        this.requirement = requirement;
    }

    @JsonProperty("requirement")
    public Requirement getRequirement() { return requirement; }

    public static class Requirement {

        private final String projectName;
        private final List<Skill> requiredSkills;
        private final BigDecimal minimumExperienceYears;
        private final String joiningDate;

        @JsonCreator
        public Requirement(
                @JsonProperty("projectName") String projectName,
                @JsonProperty("requiredSkills") List<Skill> requiredSkills,
                @JsonProperty("minimumExperienceYears") BigDecimal minimumExperienceYears,
                @JsonProperty("joiningDate") String joiningDate) {
            this.projectName = projectName;
            this.requiredSkills = requiredSkills;
            this.minimumExperienceYears = minimumExperienceYears;
            this.joiningDate = joiningDate;
        }

        @JsonProperty("projectName")
        public String getProjectName() { return projectName; }

        @JsonProperty("requiredSkills")
        public List<Skill> getRequiredSkills() { return requiredSkills; }

        @JsonProperty("minimumExperienceYears")
        public BigDecimal getMinimumExperienceYears() { return minimumExperienceYears; }

        @JsonProperty("joiningDate")
        public String getJoiningDate() { return joiningDate; }
    }

    public static class Skill {

        private final String name;
        private final Boolean mandatory;
        private final Integer minimumProficiency;

        @JsonCreator
        public Skill(
                @JsonProperty("name") String name,
                @JsonProperty("mandatory") Boolean mandatory,
                @JsonProperty("minimumProficiency") Integer minimumProficiency) {
            this.name = name;
            this.mandatory = mandatory;
            this.minimumProficiency = minimumProficiency;
        }

        @JsonProperty("name")
        public String getName() { return name; }

        @JsonProperty("mandatory")
        public Boolean getMandatory() { return mandatory; }

        @JsonProperty("minimumProficiency")
        public Integer getMinimumProficiency() { return minimumProficiency; }
    }
}