package tech.csm.llm;

import tech.csm.dto.LlmPayload;

public interface LlmClient {
    String analyze(LlmPayload payload);
}