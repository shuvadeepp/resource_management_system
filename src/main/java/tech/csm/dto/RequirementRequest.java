package tech.csm.dto;
 

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RequirementRequest(

        @NotBlank(message = "project_name must not be empty")
        @JsonProperty("project_name")
        String projectName,

        @NotEmpty(message = "required_skills must have at least one skill")
        @Valid
        @JsonProperty("required_skills")
        List<RequiredSkillRequest> requiredSkills,

        // ---- all optional below ----
        @JsonProperty("project_code") String projectCode,
        @JsonProperty("description") String description,
        @JsonProperty("business_domain") String businessDomain,
        @JsonProperty("location_id") Long locationId,
        @JsonProperty("work_mode") String workMode,
        @JsonProperty("duration_months") Integer durationMonths,
        @JsonProperty("headcount") Integer headcount,
        @JsonProperty("priority") String priority,
        @JsonProperty("minimum_experience_years") BigDecimal minimumExperienceYears,
        @JsonProperty("joining_date") LocalDate joiningDate
) {}