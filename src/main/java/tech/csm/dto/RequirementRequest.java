package tech.csm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class RequirementRequest {

    @NotBlank(message = "project_name must not be empty")
    private final String projectName;

    @NotEmpty(message = "required_skills must have at least one skill")
    @Valid
    private final List<RequiredSkillRequest> requiredSkills;

    @NotNull(message = "joining_date is required")
    private final LocalDate joiningDate;

    // ---- optional ----
    private final Integer projectId;
    private final String projectCode;
    private final String description;
    private final String location;
    private final String workMode;
    private final Integer durationMonths;
    private final Integer headcount;
    private final String priority;
    private final BigDecimal minimumExperienceYears;

    @JsonCreator
    public RequirementRequest(
            @JsonProperty("project_name") String projectName,
            @JsonProperty("required_skills") List<RequiredSkillRequest> requiredSkills,
            @JsonProperty("joining_date") LocalDate joiningDate,
            @JsonProperty("project_id") Integer projectId,
            @JsonProperty("project_code") String projectCode,
            @JsonProperty("description") String description,
            @JsonProperty("location") String location,
            @JsonProperty("work_mode") String workMode,
            @JsonProperty("duration_months") Integer durationMonths,
            @JsonProperty("headcount") Integer headcount,
            @JsonProperty("priority") String priority,
            @JsonProperty("minimum_experience_years") BigDecimal minimumExperienceYears) {
        this.projectName = projectName;
        this.requiredSkills = requiredSkills;
        this.joiningDate = joiningDate;
        this.projectId = projectId;
        this.projectCode = projectCode;
        this.description = description;
        this.location = location;
        this.workMode = workMode;
        this.durationMonths = durationMonths;
        this.headcount = headcount;
        this.priority = priority;
        this.minimumExperienceYears = minimumExperienceYears;
    }

    @JsonProperty("project_name")
    public String getProjectName() { return projectName; }

    @JsonProperty("required_skills")
    public List<RequiredSkillRequest> getRequiredSkills() { return requiredSkills; }

    @JsonProperty("joining_date")
    public LocalDate getJoiningDate() { return joiningDate; }

    @JsonProperty("project_id")
    public Integer getProjectId() { return projectId; }

    @JsonProperty("project_code")
    public String getProjectCode() { return projectCode; }

    @JsonProperty("description")
    public String getDescription() { return description; }

    @JsonProperty("location")
    public String getLocation() { return location; }

    @JsonProperty("work_mode")
    public String getWorkMode() { return workMode; }

    @JsonProperty("duration_months")
    public Integer getDurationMonths() { return durationMonths; }

    @JsonProperty("headcount")
    public Integer getHeadcount() { return headcount; }

    @JsonProperty("priority")
    public String getPriority() { return priority; }

    @JsonProperty("minimum_experience_years")
    public BigDecimal getMinimumExperienceYears() { return minimumExperienceYears; }
}