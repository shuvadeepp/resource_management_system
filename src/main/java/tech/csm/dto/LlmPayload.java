package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LlmPayload(
        @JsonProperty("requirement_id") Integer requirementId,
        @JsonProperty("requirement") RequirementRequest requirement,
        @JsonProperty("employees") List<EmployeeProfile> employees
) {}