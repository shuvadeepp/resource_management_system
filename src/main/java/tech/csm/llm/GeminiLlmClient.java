package tech.csm.llm;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tech.csm.dto.EmployeeProfile;
import tech.csm.dto.EmployeeSkillProfile;
import tech.csm.dto.LlmPayload;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

    private static final String KEY_MISSING = "AI key not configured";

    // Connection error / 5xx par retry: 3 attempts, 2s -> 4s. 429 (quota) aur timeout par kabhi retry nahi.
    private static final int MAX_ATTEMPTS = 3;
    private static final long FIRST_WAIT_MS = 2000;

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
                           @Value("${gemini.read-timeout-ms:60000}") int readTimeoutMs) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;

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

    public String getModel() { return model; }

    // ---------- purana matching flow (LlmClient) - unchanged behaviour ----------
    @Override
    public JsonNode analyze(LlmPayload payload) {
        String userPrompt = buildUserPrompt(payload);
        String text = callGemini(SYSTEM_PROMPT, userPrompt, true, 16384);
        log.info("Gemini raw reply ({} chars): {}", text.length(), text);
        return parseJson(text);
    }

    // ---------- connection test ----------
    public String ask(String question) {
        return callGemini("You are a helpful assistant. Answer briefly.", question, false, 1024);
    }

    /**
     * Compact scoring: requirement + chhota candidate pool (code, experience, skills only).
     * Reply: {"matches":[{"employee_code","match_score","explanation","learning_suggestions":[...]}]}
     */
    public JsonNode scoreCandidates(Map<String, Object> requirement, List<Map<String, Object>> candidates, int topN) {
        String prompt = """
                Requirement:
                %s

                Candidates (code = employee_code; skills as n = name, lvl = proficiency 1-5, yrs = years used):
                %s

                Task:
                1. Score every candidate 0-100 against the requirement (mandatory skills, minimum level, experience).
                   A candidate missing a mandatory skill must score 49 or less.
                2. Return only the best %d candidates, best first.
                3. explanation: at most 2 short sentences.
                4. learning_suggestions: only for required skills the candidate lacks or has below the minimum level;
                   action at most 20 words.

                Return JSON exactly in this shape:
                {"matches":[{"employee_code":"","match_score":0,"explanation":"",
                  "learning_suggestions":[{"skill":"","action":"","resource":"",
                  "resource_type":"COURSE|CERTIFICATION|DOCUMENTATION|MENTORING|PRACTICAL_ASSIGNMENT"}]}]}
                """.formatted(mapper.writeValueAsString(requirement), mapper.writeValueAsString(candidates), topN);

        String text = callGemini(SYSTEM_PROMPT, prompt, true, 8192);
        log.info("Gemini scoring reply: {} chars for {} candidates", text.length(), candidates.size());
        return parseJson(text);
    }

    /** Sirf learning actions, diye gaye gaps ke liye (on-demand generate). */
    public JsonNode suggestLearning(List<Map<String, Object>> gaps) {
        String prompt = """
                Skill gaps of one employee (current_level null = skill missing):
                %s

                For each gap give one learning action (at most 20 words), an optional resource,
                and a resource_type.

                Return JSON exactly in this shape:
                {"learning_suggestions":[{"skill":"","action":"","resource":"",
                  "resource_type":"COURSE|CERTIFICATION|DOCUMENTATION|MENTORING|PRACTICAL_ASSIGNMENT"}]}
                """.formatted(mapper.writeValueAsString(gaps));
        return parseJson(callGemini(SYSTEM_PROMPT, prompt, true, 4096));
    }

    /** Failure ka readable reason (key/raw error kabhi nahi). Jaise "AI quota reached". */
    public static String describeFailure(Throwable ex) {
        if (ex instanceof RestClientResponseException r) {
            int s = r.getStatusCode().value();
            if (s == 429) return "AI quota reached";
            if (s >= 500) return "AI service unavailable (HTTP " + s + ")";
            return "AI request rejected (HTTP " + s + ")";
        }
        if (ex instanceof ResourceAccessException) {
            for (Throwable t = ex; t != null; t = t.getCause()) {
                if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                    return "AI request timed out";
                }
            }
            return "AI service not reachable";
        }
        if (ex instanceof IllegalStateException && ex.getMessage() != null && ex.getMessage().startsWith(KEY_MISSING)) {
            return KEY_MISSING;
        }
        return "AI step failed (" + ex.getClass().getSimpleName() + ")";
    }

    // ---------- core call with retry ----------

    private String callGemini(String systemPrompt, String userPrompt, boolean jsonMode, int maxOutputTokens) {
        long waitMs = FIRST_WAIT_MS;
        for (int attempt = 1; ; attempt++) {
            try {
                return callGeminiOnce(systemPrompt, userPrompt, jsonMode, maxOutputTokens);
            } catch (ResourceAccessException | RestClientResponseException ex) {
                // Timeout par retry nahi: model already slow hai, dobara bhejne se wait double hota hai.
                boolean retryable = ex instanceof ResourceAccessException
                        ? !"AI request timed out".equals(describeFailure(ex))
                        : ((RestClientResponseException) ex).getStatusCode().is5xxServerError();
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
                log.warn("Gemini call failed ({}). Retry {}/{} after {} ms",
                        describeFailure(ex), attempt, MAX_ATTEMPTS - 1, waitMs);
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

    private String callGeminiOnce(String systemPrompt, String userPrompt, boolean jsonMode, int maxOutputTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(KEY_MISSING + " (GEMINI_API_KEY)");
        }

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", maxOutputTokens);
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
                throw new IllegalStateException("Gemini returned an empty response");
            }
            return text;

        } catch (RestClientResponseException ex) {
            log.error("Gemini API returned {}", ex.getStatusCode());
            throw ex;
        }
    }

    private JsonNode parseJson(String text) {
        // ```json fences hata do agar model ne lagaye hon
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

    // ---------- purana prompt (analyze) ----------
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
