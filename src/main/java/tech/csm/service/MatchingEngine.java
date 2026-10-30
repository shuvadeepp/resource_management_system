package tech.csm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tech.csm.llm.GeminiLlmClient;
import tech.csm.service.MatchingData.Candidate;
import tech.csm.service.MatchingData.CandidateSkill;
import tech.csm.service.MatchingData.FinalMatch;
import tech.csm.service.MatchingData.Gap;
import tech.csm.service.MatchingData.LearningItem;
import tech.csm.service.MatchingData.Outcome;
import tech.csm.service.MatchingData.ReqSkill;
import tech.csm.service.MatchingData.Scored;
import tech.csm.service.MatchingData.Spec;
import tools.jackson.databind.JsonNode;

/**
 * Pool banana (Java pre-score) -> Gemini se score (optional) -> final top N.
 * Gemini fail ho (429 / 503 / timeout / key nahi) to run fail nahi hota: rule
 * score se hi results bante hain aur aiError me readable reason aata hai.
 * Category aur rank hamesha Java me score se nikalte hain.
 * Isme koi DB call nahi - transaction ke bahar chalane ke liye safe.
 */
@Service
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    private final MatchScorer scorer;
    private final GeminiLlmClient gemini;

    @Value("${app.matching.use-llm:true}")
    private boolean useLlm;

    @Value("${app.matching.max-candidates:15}")
    private int maxCandidates;

    @Value("${app.matching.max-results:5}")
    private int maxResults;

    public MatchingEngine(MatchScorer scorer, GeminiLlmClient gemini) {
        this.scorer = scorer;
        this.gemini = gemini;
    }

    public boolean isLlmEnabled() {
        return useLlm;
    }

    /** Eligible candidates (>= 1 required ya related skill), rule score se top max-candidates. */
    public List<Scored> buildPool(Spec spec, List<Candidate> candidates) {
        return candidates.stream()
                .map(c -> scorer.score(spec, c))
                .filter(scorer::eligible)
                .sorted(Comparator.comparingDouble(Scored::ruleScore).reversed())
                .limit(maxCandidates)
                .toList();
    }

    public Outcome score(Spec spec, List<Scored> pool) {
        if (pool.isEmpty()) {
            return new Outcome(List.of(), 0, useLlm ? MatchingData.AI_USED : MatchingData.AI_DISABLED, null);
        }
        if (!useLlm) {
            return new Outcome(byRules(spec, pool), pool.size(), MatchingData.AI_DISABLED, null);
        }
        try {
            JsonNode reply = gemini.scoreCandidates(requirementPayload(spec), candidatePayload(pool), maxResults);
            return new Outcome(byAi(spec, pool, reply), pool.size(), MatchingData.AI_USED, null);
        } catch (Exception ex) {
            String reason = GeminiLlmClient.describeFailure(ex);
            log.warn("AI scoring failed, using rule scores: {}", reason);
            return new Outcome(byRules(spec, pool), pool.size(), MatchingData.AI_FAILED,
                    reason + ", scored by rules only");
        }
    }

    // ---------------- final lists ----------------

    private List<FinalMatch> byRules(Spec spec, List<Scored> pool) {
        List<FinalMatch> out = new ArrayList<>();
        int rank = 1;
        for (Scored s : pool) { // pool pehle se rule score se sorted hai
            if (rank > maxResults) {
                break;
            }
            BigDecimal score = MatchScorer.toScore(s.ruleScore());
            List<LearningItem> learning = s.gaps().stream().map(scorer::templateLearning).toList();
            out.add(new FinalMatch(s, score, MatchScorer.category(score), rank++,
                    scorer.explanation(s, spec), learning, "RULES"));
        }
        return out;
    }

    private List<FinalMatch> byAi(Spec spec, List<Scored> pool, JsonNode reply) {
        Map<String, Scored> byCode = new HashMap<>();
        for (Scored s : pool) {
            byCode.put(s.candidate().employeeCode(), s);
        }

        record AiPick(Scored scored, double score, JsonNode node) {
        }
        List<AiPick> picks = new ArrayList<>();
        for (JsonNode m : reply.path("matches")) {
            Scored s = byCode.remove(m.path("employee_code").asString(""));
            if (s == null) {
                continue; // pool ke bahar ka / duplicate code - ignore
            }
            double v = m.path("match_score").asDouble(s.ruleScore());
            if (s.mandatoryMissing()) {
                v = Math.min(v, 49); // rule Java me bhi enforce
            }
            picks.add(new AiPick(s, v, m));
        }
        picks.sort(Comparator.comparingDouble(AiPick::score).reversed());

        List<FinalMatch> out = new ArrayList<>();
        int rank = 1;
        for (AiPick p : picks) {
            if (rank > maxResults) {
                break;
            }
            BigDecimal score = MatchScorer.toScore(p.score());
            String explanation = p.node().path("explanation").asString("");
            if (explanation.isBlank()) {
                explanation = scorer.explanation(p.scored(), spec);
            }
            out.add(new FinalMatch(p.scored(), score, MatchScorer.category(score), rank++,
                    truncate(explanation, 1000), mergeLearning(p.scored(), p.node().path("learning_suggestions")),
                    "AI"));
        }
        return out;
    }

    /** Gaps Java decide karta hai; Gemini sirf action/resource deta hai. Jis gap ka AI text nahi, uska template. */
    private List<LearningItem> mergeLearning(Scored s, JsonNode suggestions) {
        Map<String, JsonNode> bySkill = new HashMap<>();
        for (JsonNode n : suggestions) {
            String skill = n.path("skill").asString("");
            if (!skill.isBlank()) {
                bySkill.putIfAbsent(skill.trim().toLowerCase(), n);
            }
        }
        List<LearningItem> out = new ArrayList<>();
        for (Gap g : s.gaps()) {
            JsonNode n = bySkill.get(g.skill().skillName().toLowerCase());
            String action = n == null ? "" : n.path("action").asString("");
            if (action.isBlank()) {
                out.add(scorer.templateLearning(g));
                continue;
            }
            String resource = n.path("resource").asString("");
            out.add(new LearningItem(g.skill(), g.currentLevel(), truncate(action, 500),
                    resource.isBlank() ? null : truncate(resource, 1000),
                    MatchScorer.resourceType(n.path("resource_type").asString(null)),
                    MatchScorer.gapDescription(g)));
        }
        return out;
    }

    // ---------------- compact Gemini payload ----------------

    private Map<String, Object> requirementPayload(Spec spec) {
        List<Map<String, Object>> skills = new ArrayList<>();
        for (ReqSkill r : spec.skills()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.skillName());
            m.put("mandatory", r.mandatory());
            m.put("min_level", r.minProficiency());
            skills.add(m);
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("skills", skills);
        if (spec.minExperienceMonths() != null && spec.minExperienceMonths() > 0) {
            req.put("min_experience_years", Math.round(spec.minExperienceMonths() / 12.0 * 10) / 10.0);
        }
        return req;
    }

    // Naam/email kabhi nahi - sirf code, experience, skills
    private List<Map<String, Object>> candidatePayload(List<Scored> pool) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Scored s : pool) {
            Candidate c = s.candidate();
            List<Map<String, Object>> skills = new ArrayList<>();
            for (CandidateSkill cs : c.skills()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("n", cs.skillName());
                m.put("lvl", cs.proficiency());
                m.put("yrs", cs.experienceMonths() == null ? null : Math.round(cs.experienceMonths() / 12.0 * 10) / 10.0);
                skills.add(m);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", c.employeeCode());
            m.put("exp_years", c.experienceYears());
            m.put("skills", skills);
            out.add(m);
        }
        return out;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
