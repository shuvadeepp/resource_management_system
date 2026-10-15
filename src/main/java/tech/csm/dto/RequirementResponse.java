package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

public record RequirementResponse(
        @JsonProperty("status") String status,
        @JsonProperty("message") String message,
        @JsonProperty("requirement_id") Integer requirementId,
        @JsonProperty("requirement_status") String requirementStatus,
        @JsonProperty("employees_sent") int employeesSent,
        @JsonProperty("llm_result") JsonNode llmResult
) {}