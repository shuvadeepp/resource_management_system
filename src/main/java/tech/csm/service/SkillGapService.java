package tech.csm.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.exception.ApiException;
import tech.csm.llm.GeminiLlmClient;
import tech.csm.model.LearningRecommendation;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.ProjectRequirement;
import tech.csm.model.Skill;
import tech.csm.repository.LearningRecommendationRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
import tech.csm.repository.RequiredSkillRepository;
import tech.csm.service.MatchingData.Candidate;
import tech.csm.service.MatchingData.Gap;
import tech.csm.service.MatchingData.LearningItem;
import tech.csm.service.MatchingData.Spec;
import tools.jackson.databind.JsonNode;

/**
 * Skill gaps deterministic hain: employee ki active employee_skills vs
 * requirement ki required_skills (missing + required level se neeche).
 * LLM sirf learning action likhta hai.
 */
@Service
public class SkillGapService {

    private static final Set<String> VALID_REVIEW = Set.of("APPROVED", "REJECTED", "IN_PROGRESS", "COMPLETED");

    private record Context(Spec spec, Map<Integer, Skill> skillsById) {
    }

    private record GapData(MatchingResult result, Candidate candidate, Context ctx, List<Gap> gaps,
                           List<LearningRecommendation> recs) {
    }

    private final MatchingResultRepository matchingResultRepository;
    private final MatchingRunRepository matchingRunRepository;
    private final RequiredSkillRepository requiredSkillRepository;
    private final LearningRecommendationRepository learningRecommendationRepository;
    private final RequirementQueryService queryService;
    private final CandidatePoolService candidatePool;
    private final MatchScorer scorer;
    private final MatchingEngine engine;
    private final GeminiLlmClient gemini;
    private final MatchingPersistenceService persistence;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public SkillGapService(MatchingResultRepository matchingResultRepository,
                           MatchingRunRepository matchingRunRepository,
                           RequiredSkillRepository requiredSkillRepository,
                           LearningRecommendationRepository learningRecommendationRepository,
                           RequirementQueryService queryService,
                           CandidatePoolService candidatePool,
                           MatchScorer scorer,
                           MatchingEngine engine,
                           GeminiLlmClient gemini,
                           MatchingPersistenceService persistence,
                           CurrentUserService currentUser,
                           AuditService audit) {
        this.matchingResultRepository = matchingResultRepository;
        this.matchingRunRepository = matchingRunRepository;
        this.requiredSkillRepository = requiredSkillRepository;
        this.learningRecommendationRepository = learningRecommendationRepository;
        this.queryService = queryService;
        this.candidatePool = candidatePool;
        this.scorer = scorer;
        this.engine = engine;
        this.gemini = gemini;
        this.persistence = persistence;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    // ---------------- read ----------------

    @Transactional(readOnly = true)
    public Map<String, Object> forResult(Integer matchingResultId) {
        GapData data = load(matchingResultId);
        return toMap(data, data.recs());
    }

    /** Requirement ke latest finished run ke har match ke gaps (Skill Growth screen). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forRequirement(Integer requirementId) {
        ProjectRequirement req = queryService.findRequirement(requirementId);
        MatchingRun run = queryService.resolveRun(requirementId, null);
        if (run == null) {
            return List.of();
        }
        List<MatchingResult> results = matchingResultRepository.findByMatchingRunIdOrderByRankPositionAsc(run.getMatchingRunId());
        if (results.isEmpty()) {
            return List.of();
        }
        Context ctx = context(req);
        Map<Integer, Candidate> candidates = candidatePool.loadByIds(
                results.stream().map(MatchingResult::getEmployeeId).collect(Collectors.toSet()));
        Map<Integer, List<LearningRecommendation>> recs = learningRecommendationRepository
                .findByMatchingResultIdIn(results.stream().map(MatchingResult::getMatchingResultId).toList())
                .stream().collect(Collectors.groupingBy(LearningRecommendation::getMatchingResultId));

        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchingResult mr : results) {
            Candidate c = candidates.get(mr.getEmployeeId());
            List<LearningRecommendation> r = recs.getOrDefault(mr.getMatchingResultId(), List.of());
            out.add(toMap(new GapData(mr, c, ctx, gapsFor(ctx, c), r), r));
        }
        return out;
    }

    // ---------------- review ----------------

    @Transactional
    public Map<String, Object> review(Integer learningRecommendationId, String status, String comments) {
        String value = status == null ? "" : status.trim().toUpperCase();
        if (!VALID_REVIEW.contains(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "review_status must be one of APPROVED, REJECTED, IN_PROGRESS, COMPLETED");
        }
        LearningRecommendation lr = learningRecommendationRepository.findById(learningRecommendationId)
                .orElseThrow(() -> new NoSuchElementException("Learning recommendation not found: " + learningRecommendationId));

        Map<String, Object> old = new LinkedHashMap<>();
        old.put("review_status", lr.getReviewStatus());
        old.put("review_comments", lr.getReviewComments());

        Integer userId = currentUser.currentUserId();
        lr.setReviewStatus(value);
        lr.setReviewComments(comments == null || comments.isBlank() ? null : comments.trim());
        lr.setReviewedBy(userId);
        lr.setReviewedAt(MatchingPersistenceService.nowIst());
        lr = learningRecommendationRepository.save(lr);

        Map<String, Object> now = new LinkedHashMap<>();
        now.put("review_status", value);
        now.put("review_comments", lr.getReviewComments());
        audit.log(userId, AuditService.LEARNING_REVIEWED, "learning_recommendations", learningRecommendationId, old, now, null);

        Map<String, Object> body = RequirementQueryService.learningMaps(List.of(lr), candidatePool.skillsById()).get(0);
        body.put("matching_result_id", lr.getMatchingResultId());
        body.put("reviewed_by", lr.getReviewedBy());
        body.put("reviewed_at", lr.getReviewedAt());
        return body;
    }

    // ---------------- on-demand generate ----------------

    /**
     * Jin gaps ki recommendation nahi hai unke liye ek Gemini call. Gemini fail ho
     * ya band ho to 500 nahi: gaps + jo recommendations hain + "note".
     * Gemini call kisi transaction ke andar nahi hota.
     */
    public Map<String, Object> generate(Integer matchingResultId) {
        GapData data = load(matchingResultId);
        Set<Integer> covered = data.recs().stream().map(LearningRecommendation::getSkillId).collect(Collectors.toSet());
        List<Gap> open = data.gaps().stream()
                .filter(g -> g.skill().skillId() != null && !covered.contains(g.skill().skillId()))
                .toList();

        Map<String, Object> body = toMap(data, data.recs());
        if (open.isEmpty()) {
            body.put("note", data.gaps().isEmpty() ? "No skill gaps for this match"
                    : "Every gap already has a learning recommendation");
            return body;
        }
        if (!engine.isLlmEnabled()) {
            body.put("note", "AI is turned off (app.matching.use-llm=false); no recommendations generated");
            return body;
        }

        JsonNode reply;
        try {
            reply = gemini.suggestLearning(gapPayload(open));
        } catch (Exception ex) {
            body.put("note", GeminiLlmClient.describeFailure(ex) + "; no recommendations generated, try again later");
            return body;
        }

        Map<String, JsonNode> bySkill = new HashMap<>();
        for (JsonNode n : reply.path("learning_suggestions")) {
            String skill = n.path("skill").asString("");
            if (!skill.isBlank()) {
                bySkill.putIfAbsent(skill.trim().toLowerCase(), n);
            }
        }
        List<LearningItem> items = new ArrayList<>();
        for (Gap g : open) {
            JsonNode n = bySkill.get(g.skill().skillName().toLowerCase());
            String action = n == null ? "" : n.path("action").asString("");
            if (action.isBlank()) {
                items.add(scorer.templateLearning(g));
            } else {
                String resource = n.path("resource").asString("");
                items.add(new LearningItem(g.skill(), g.currentLevel(),
                        action.length() > 500 ? action.substring(0, 500) : action,
                        resource.isBlank() ? null : (resource.length() > 1000 ? resource.substring(0, 1000) : resource),
                        MatchScorer.resourceType(n.path("resource_type").asString(null)),
                        MatchScorer.gapDescription(g)));
            }
        }
        List<LearningRecommendation> saved = persistence.saveRecommendations(matchingResultId, items);
        audit.log(currentUser.currentUserId(), AuditService.LEARNING_GENERATED, "matching_results", matchingResultId,
                null, Map.of("created", saved.size()), null);

        List<LearningRecommendation> all = new ArrayList<>(data.recs());
        all.addAll(saved);
        body = toMap(data, all);
        body.put("note", "Generated " + saved.size() + " learning recommendation(s)");
        return body;
    }

    // ---------------- helpers ----------------

    private GapData load(Integer matchingResultId) {
        MatchingResult mr = matchingResultRepository.findById(matchingResultId)
                .orElseThrow(() -> new NoSuchElementException("Matching result not found: " + matchingResultId));
        MatchingRun run = matchingRunRepository.findById(mr.getMatchingRunId())
                .orElseThrow(() -> new NoSuchElementException("Matching run not found: " + mr.getMatchingRunId()));
        Context ctx = context(queryService.findRequirement(run.getRequirementId()));
        Candidate c = candidatePool.loadByIds(Set.of(mr.getEmployeeId())).get(mr.getEmployeeId());
        return new GapData(mr, c, ctx, gapsFor(ctx, c),
                learningRecommendationRepository.findByMatchingResultId(matchingResultId));
    }

    private Context context(ProjectRequirement req) {
        Map<Integer, Skill> skillsById = candidatePool.skillsById();
        Spec spec = candidatePool.specFromRequirement(
                requiredSkillRepository.findByRequirementId(req.getRequirementId()),
                req.getMinimumExperienceMonths(), skillsById);
        return new Context(spec, skillsById);
    }

    private List<Gap> gapsFor(Context ctx, Candidate c) {
        return c == null ? List.of() : scorer.score(ctx.spec(), c).gaps();
    }

    private Map<String, Object> toMap(GapData d, List<LearningRecommendation> recs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("matching_result_id", d.result().getMatchingResultId());
        m.put("employee_id", d.result().getEmployeeId());
        m.put("employee_code", d.candidate() != null ? d.candidate().employeeCode() : null);
        m.put("resource_name", d.candidate() != null ? d.candidate().name() : null);
        m.put("match_category", d.result().getMatchCategory());

        List<Map<String, Object>> gaps = new ArrayList<>();
        for (Gap g : d.gaps()) {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("skill_name", g.skill().skillName());
            gm.put("required_level", g.skill().minProficiency());
            gm.put("current_level", g.currentLevel());
            gm.put("mandatory", g.skill().mandatory());
            gm.put("gap_description", MatchScorer.gapDescription(g));
            gaps.add(gm);
        }
        m.put("gaps", gaps);
        m.put("learning_recommendations", RequirementQueryService.learningMaps(recs, d.ctx().skillsById()));
        return m;
    }

    private List<Map<String, Object>> gapPayload(List<Gap> gaps) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Gap g : gaps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skill", g.skill().skillName());
            m.put("current_level", g.currentLevel());
            m.put("target_level", g.skill().minProficiency());
            m.put("mandatory", g.skill().mandatory());
            out.add(m);
        }
        return out;
    }
}
