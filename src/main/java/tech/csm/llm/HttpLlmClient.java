package tech.csm.llm;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

import tech.csm.dto.LlmPayload;
import tech.csm.dto.LlmRequest;
import tech.csm.dto.RequiredSkillRequest;
import tech.csm.dto.RequirementRequest;

@Component
public class HttpLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(HttpLlmClient.class);

    private final RestClient restClient;

    public HttpLlmClient(@Value("${llm.api.url:http://10.30.7.36:5000/api/matches}") String llmUrl,
                         @Value("${llm.api.connect-timeout-ms:5000}") int connectTimeoutMs,
                         @Value("${llm.api.read-timeout-ms:60000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(llmUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public JsonNode analyze(LlmPayload payload) {
        LlmRequest body = toLlmRequest(payload.requirement());
        try {
            return restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            log.error("LLM API returned {} : {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        }
    }

    private LlmRequest toLlmRequest(RequirementRequest req) {
        List<LlmRequest.Skill> skills = new ArrayList<>();
        for (RequiredSkillRequest rs : req.requiredSkills()) {
            skills.add(new LlmRequest.Skill(rs.skillName(), rs.mandatory(), rs.minimumProficiency()));
        }

        LlmRequest.Requirement requirement = new LlmRequest.Requirement(
                req.projectName(),
                skills,
                req.minimumExperienceYears(),
                req.joiningDate() != null ? req.joiningDate().toString() : null);

        return new LlmRequest(requirement);
    }
}