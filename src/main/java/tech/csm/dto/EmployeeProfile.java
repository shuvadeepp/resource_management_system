package tech.csm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EmployeeProfile {

    private final Integer employeeId;
    private final String employeeCode;
    private final String employeeName;
    private final Long designationId;
    private final Integer departmentId;
    private final Integer locationId;
    private final Double totalExperienceYears;
    private final List<EmployeeSkillProfile> skills;

    @JsonCreator
    public EmployeeProfile(
            @JsonProperty("employee_id") Integer employeeId,
            @JsonProperty("employee_code") String employeeCode,
            @JsonProperty("employee_name") String employeeName,
            @JsonProperty("designation_id") Long designationId,
            @JsonProperty("department_id") Integer departmentId,
            @JsonProperty("location_id") Integer locationId,
            @JsonProperty("total_experience_years") Double totalExperienceYears,
            @JsonProperty("skills") List<EmployeeSkillProfile> skills) {
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.employeeName = employeeName;
        this.designationId = designationId;
        this.departmentId = departmentId;
        this.locationId = locationId;
        this.totalExperienceYears = totalExperienceYears;
        this.skills = skills;
    }

    @JsonProperty("employee_id")
    public Integer getEmployeeId() { return employeeId; }

    @JsonProperty("employee_code")
    public String getEmployeeCode() { return employeeCode; }

    @JsonProperty("employee_name")
    public String getEmployeeName() { return employeeName; }

    @JsonProperty("designation_id")
    public Long getDesignationId() { return designationId; }

    @JsonProperty("department_id")
    public Integer getDepartmentId() { return departmentId; }

    @JsonProperty("location_id")
    public Integer getLocationId() { return locationId; }

    @JsonProperty("total_experience_years")
    public Double getTotalExperienceYears() { return totalExperienceYears; }

    @JsonProperty("skills")
    public List<EmployeeSkillProfile> getSkills() { return skills; }
}