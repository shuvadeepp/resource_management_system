package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

public class RequirementResponse {

    private final String status;
    private final String message;
    private final Integer requirementId;
    private final String requirementStatus;
    private final int employeesSent;
    private final JsonNode llmResult;

    @JsonCreator
    public RequirementResponse(
            @JsonProperty("status") String status,
            @JsonProperty("message") String message,
            @JsonProperty("requirement_id") Integer requirementId,
            @JsonProperty("requirement_status") String requirementStatus,
            @JsonProperty("employees_sent") int employeesSent,
            @JsonProperty("llm_result") JsonNode llmResult) {
        this.status = status;
        this.message = message;
        this.requirementId = requirementId;
        this.requirementStatus = requirementStatus;
        this.employeesSent = employeesSent;
        this.llmResult = llmResult;
    }

    @JsonProperty("status")
    public String getStatus() { return status; }

    @JsonProperty("message")
    public String getMessage() { return message; }

    @JsonProperty("requirement_id")
    public Integer getRequirementId() { return requirementId; }

    @JsonProperty("requirement_status")
    public String getRequirementStatus() { return requirementStatus; }

    @JsonProperty("employees_sent")
    public int getEmployeesSent() { return employeesSent; }

    @JsonProperty("llm_result")
    public JsonNode getLlmResult() { return llmResult; }
}