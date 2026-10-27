package tech.csm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;

public record RequirementRequest(

        @JsonProperty("project_name") String projectName,
        @Valid @JsonProperty("required_skills") List<RequiredSkillRequest> requiredSkills,
        @JsonProperty("joining_date") LocalDate joiningDate,
        @JsonProperty("minimum_experience_years") BigDecimal minimumExperienceYears,

        // new search filters
        @JsonProperty("designation") String designation,
        @JsonProperty("department") String department,

        @JsonProperty("project_id") Integer projectId,
        @JsonProperty("project_code") String projectCode,
        @JsonProperty("description") String description,
        @JsonProperty("location") String location,
        @JsonProperty("work_mode") String workMode,
        @JsonProperty("duration_months") Integer durationMonths,
        @JsonProperty("headcount") Integer headcount,
        @JsonProperty("priority") String priority
) {}