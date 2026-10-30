package tech.csm.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

public class RequirementResponse {

    private final String status;
    private final String message;
    private final Integer requirementId;
    private final String requirementStatus;
    private final int employeesSent;
    private final JsonNode llmResult;

    // naye (additive) fields
    private final Boolean saved;
    private final List<Map<String, Object>> employees;
    private final Integer matchingRunId;
    private final String runStatus;

    @JsonCreator
    public RequirementResponse(
            @JsonProperty("status") String status,
            @JsonProperty("message") String message,
            @JsonProperty("requirement_id") Integer requirementId,
            @JsonProperty("requirement_status") String requirementStatus,
            @JsonProperty("employees_sent") int employeesSent,
            @JsonProperty("llm_result") JsonNode llmResult) {
        this(status, message, requirementId, requirementStatus, employeesSent, llmResult, null, null, null, null);
    }

    public RequirementResponse(String status, String message, Integer requirementId, String requirementStatus,
                               int employeesSent, JsonNode llmResult, Boolean saved,
                               List<Map<String, Object>> employees, Integer matchingRunId, String runStatus) {
        this.status = status;
        this.message = message;
        this.requirementId = requirementId;
        this.requirementStatus = requirementStatus;
        this.employeesSent = employeesSent;
        this.llmResult = llmResult;
        this.saved = saved;
        this.employees = employees;
        this.matchingRunId = matchingRunId;
        this.runStatus = runStatus;
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

    @JsonProperty("saved")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getSaved() { return saved; }

    @JsonProperty("matching_run_id")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getMatchingRunId() { return matchingRunId; }

    @JsonProperty("run_status")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getRunStatus() { return runStatus; }

    @JsonProperty("employees")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<Map<String, Object>> getEmployees() { return employees; }
}
