package tech.csm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmployeeProfile(
        @JsonProperty("employee_id") Integer employeeId,
        @JsonProperty("employee_code") String employeeCode,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("designation_id") Long designationId,
        @JsonProperty("department_id") Integer departmentId,
        @JsonProperty("location_id") Long locationId,
        @JsonProperty("total_experience_years") Double totalExperienceYears,
        @JsonProperty("skills") List<EmployeeSkillProfile> skills
) {}