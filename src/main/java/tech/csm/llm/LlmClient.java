package tech.csm.llm;

import tools.jackson.databind.JsonNode;

import tech.csm.dto.LlmPayload;

public interface LlmClient {
	JsonNode analyze(LlmPayload payload);
}