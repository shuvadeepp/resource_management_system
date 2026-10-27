package tech.csm.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tech.csm.dto.EmployeeProfile;
import tech.csm.dto.EmployeeSkillProfile;
import tech.csm.dto.LlmPayload;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private static final String SYSTEM_PROMPT = """
            You are a resource-matching assistant for an IT company.
            Use ONLY the data provided. Never invent skills, experience, certifications or scores.
            If data is missing, say "not available".
            Do not use age, gender, religion or any personal characteristic.
            Related skills are not proof of equivalence: mark them "transferable, needs validation".
            A candidate who lacks a mandatory skill must not be rated above 49.
            Return ONLY valid JSON, no extra text.
            """;

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final double temperature;

    public GeminiLlmClient(ObjectMapper mapper,
                           @Value("${gemini.api-key:}") String apiKey,
                           @Value("${gemini.model}") String model,
                           @Value("${gemini.base-url}") String baseUrl,
                           @Value("${gemini.temperature:0.2}") double temperature,
                           @Value("${gemini.connect-timeout-ms:10000}") int connectTimeoutMs,
                           @Value("${gemini.read-timeout-ms:90000}") int readTimeoutMs) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;

		/*
		 * SimpleClientHttpRequestFactory factory = new
		 * SimpleClientHttpRequestFactory();
		 * factory.setConnectTimeout(connectTimeoutMs);
		 * factory.setReadTimeout(readTimeoutMs);
		 * 
		 * this.restClient = RestClient.builder() .baseUrl(baseUrl)
		 * .requestFactory(factory) .build();
		 */
        
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    // ---------- used by the matching flow ----------
    @Override
    public JsonNode analyze(LlmPayload payload) {
        String userPrompt = buildUserPrompt(payload);
        String text = callGemini(SYSTEM_PROMPT, userPrompt, true);

        log.info("Gemini raw reply ({} chars): {}", text.length(), text);

        // remove ```json fences if the model added them
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }

        try {
            return mapper.readTree(cleaned);
        } catch (Exception ex) {
            log.error("Could not parse Gemini reply as JSON", ex);
            throw ex;
        }
    }
    // ---------- used by the connection test ----------
    public String ask(String question) {
        return callGemini("You are a helpful assistant. Answer briefly.", question, false);
    }

    // ---------- core call ----------
     
    private String callGemini(String systemPrompt, String userPrompt, boolean jsonMode) {
        int maxAttempts = 4;
        long waitMs = 3000;

        for (int attempt = 1; ; attempt++) {
            try {
                return callGeminiOnce(systemPrompt, userPrompt, jsonMode);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                boolean retryable = status == 503 || status == 429 || status == 500;
                if (!retryable || attempt >= maxAttempts) {
                    throw ex;
                }
                log.warn("Gemini returned {}. Retry {}/{} after {} ms", status, attempt, maxAttempts - 1, waitMs);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
                waitMs *= 2;   // 3s, 6s, 12s
            }
        }
    }
    
 // ---------- retry wrapper ----------
    private String callGemini1(String systemPrompt, String userPrompt, boolean jsonMode) {
        int maxAttempts = 4;
        long waitMs = 3000;

        for (int attempt = 1; ; attempt++) {
            try {
                return callGeminiOnce(systemPrompt, userPrompt, jsonMode);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                boolean retryable = status == 503 || status == 429 || status == 500;
                if (!retryable || attempt >= maxAttempts) {
                    throw ex;
                }
                log.warn("Gemini returned {}. Retry {}/{} after {} ms", status, attempt, maxAttempts - 1, waitMs);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
                waitMs *= 2;
            }
        }
    }

    // ---------- single call (purani body, sirf naam badla) ----------
    private String callGeminiOnce(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set");
        }

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", 16384);
        if (jsonMode) {
            generationConfig.put("responseMimeType", "application/json");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))));
        body.put("generationConfig", generationConfig);

        try {
        	byte[] bytes = restClient.post()
        	        .uri("/models/{model}:generateContent", model)
        	        .header("x-goog-api-key", apiKey)
        	        .contentType(MediaType.APPLICATION_JSON)
        	        .accept(MediaType.APPLICATION_JSON)
        	        .body(body)
        	        .retrieve()
        	        .body(byte[].class);

        	String raw = bytes == null ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("Gemini returned an empty body");
            }

            JsonNode response;
            try {
                response = mapper.readTree(raw);
            } catch (Exception parseEx) {
                String preview = raw.length() > 500 ? raw.substring(0, 500) : raw;
                log.error("Gemini reply is not JSON. First 500 chars: {}", preview);
                throw new IllegalStateException("Reply is not JSON: " + preview);
            }

            String text = response.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").textValue();

            if (text == null) {
                log.error("Gemini returned no text. Full response: {}", response);
                throw new IllegalStateException("Gemini returned an empty response: " + response);
            }
            return text;

        } catch (RestClientResponseException ex) {
            log.error("Gemini API returned {} : {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        }
    }
   /* private String callGemini(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set");
        }

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", 8192);
        if (jsonMode) {
            generationConfig.put("responseMimeType", "application/json");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))));
        body.put("generationConfig", generationConfig);

        try {
            JsonNode response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String text = response.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").textValue();

            if (text == null) {
                log.error("Gemini returned no text. Full response: {}", response);
                throw new IllegalStateException("Gemini returned an empty response");
            }
            return text;

        } catch (RestClientResponseException ex) {
            log.error("Gemini API returned {} : {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        }
    }*/

    // ---------- prompt ----------
    private String buildUserPrompt(LlmPayload payload) {
        // Employee names are NOT sent, only employee_code (free tier: do not share personal data)
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (EmployeeProfile e : payload.getEmployees()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("employee_code", e.getEmployeeCode());
            c.put("total_experience_years", e.getTotalExperienceYears());

            List<Map<String, Object>> skills = new ArrayList<>();
            if (e.getSkills() != null) {
                for (EmployeeSkillProfile s : e.getSkills()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", s.getSkillName());
                    m.put("proficiency", s.getProficiencyLevel());
                    m.put("experience_months", s.getExperienceMonths());
                    m.put("validation_status", s.getValidationStatus());
                    skills.add(m);
                }
            }
            c.put("skills", skills);
            candidates.add(c);
        }

        String requirementJson = mapper.writeValueAsString(payload.getRequirement());
        String candidatesJson = mapper.writeValueAsString(candidates);

        return """
                Requirement:
                %s

                Candidates:
                %s

                Task:
                1. Compare every candidate with the requirement (mandatory skills, minimum proficiency, experience).
                2. Give each candidate a match_score from 0 to 100.
                3. Return only candidates with match_score >= 40, best first, maximum 5.

                Return JSON in exactly this shape:
                {
                  "total_qualified_matches": 0,
                  "matches": [
                    {
                      "employee_code": "",
                      "match_score": 0,
                      "category": "Strong Match | Potential Match | Development Candidate",
                      "matching_skills": [],
                      "below_required_proficiency": [{"skill": "", "has": 0, "needs": 0}],
                      "missing_skills": [],
                      "transferable_skills": [{"skill": "", "related_to": "", "note": "needs validation"}],
                      "explanation": "2-3 sentences",
                      "learning_suggestions": [{"skill": "", "action": ""}]
                    }
                  ],
                  "note": "AI recommendations require manager review."
                }
                """.formatted(requirementJson, candidatesJson);
    }
}