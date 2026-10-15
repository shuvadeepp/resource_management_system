package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class LlmPayload {

    private final Integer requirementId;
    private final RequirementRequest requirement;
    private final List<EmployeeProfile> employees;

    @JsonCreator
    public LlmPayload(
            @JsonProperty("requirement_id") Integer requirementId,
            @JsonProperty("requirement") RequirementRequest requirement,
            @JsonProperty("employees") List<EmployeeProfile> employees) {
        this.requirementId = requirementId;
        this.requirement = requirement;
        this.employees = employees;
    }

    @JsonProperty("requirement_id")
    public Integer getRequirementId() { return requirementId; }

    @JsonProperty("requirement")
    public RequirementRequest getRequirement() { return requirement; }

    @JsonProperty("employees")
    public List<EmployeeProfile> getEmployees() { return employees; }
}