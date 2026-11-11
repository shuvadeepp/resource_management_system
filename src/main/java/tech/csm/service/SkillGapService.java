package tech.csm.service;

import java.util.ArrayList;
import java.util.Comparator;
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
import tech.csm.model.LearningResource;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.ProjectRequirement;
import tech.csm.model.Skill;
import tech.csm.model.SkillGap;
import tech.csm.repository.LearningRecommendationRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
import tech.csm.repository.RequiredSkillRepository;
import tech.csm.repository.SkillGapRepository;
import tech.csm.service.MatchingData.Candidate;
import tech.csm.service.MatchingData.Gap;
import tech.csm.service.MatchingData.LearningItem;
import tech.csm.service.MatchingData.ReqSkill;
import tech.csm.service.MatchingData.Spec;
import tools.jackson.databind.JsonNode;

/**
 * Skill gaps deterministic hain (Java, AI nahi). Run khatam hone par skill_gaps table me store hote
 * hain (MatchingPersistenceService), isliye training ke baad employee ki skill badalne se purane run
 * ke gaps nahi badalte. Jin purane runs ke skill_gaps rows nahi hain unke liye wahi gaps live
 * (employee_skills vs required_skills) nikalte hain. LLM sirf learning action likhta hai.
 */
@Service
public class SkillGapService {

    private static final Set<String> VALID_REVIEW = Set.of("APPROVED", "REJECTED", "IN_PROGRESS", "COMPLETED");

    private record Context(Spec spec, Map<Integer, Skill> skillsById, String requirementPriority) {
    }

    /** Ek gap ka read-model - stored row se ya purane runs ke liye live calculation se. */
    private record GapView(Integer skillGapId, Integer skillId, String skillName, int required, Integer current,
                           boolean mandatory, String gapType, int gapSize, String priority, String reason) {

        Gap toGap() {
            return new Gap(new ReqSkill(skillId, skillName, null, mandatory, required), current);
        }
    }

    private record GapData(MatchingResult result, Candidate candidate, Context ctx, List<GapView> gaps,
                           boolean stored, List<LearningRecommendation> recs) {
    }

    private final MatchingResultRepository matchingResultRepository;
    private final MatchingRunRepository matchingRunRepository;
    private final RequiredSkillRepository requiredSkillRepository;
    private final LearningRecommendationRepository learningRecommendationRepository;
    private final SkillGapRepository skillGapRepository;
    private final LearningCatalogService catalog;
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
                           SkillGapRepository skillGapRepository,
                           LearningCatalogService catalog,
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
        this.skillGapRepository = skillGapRepository;
        this.catalog = catalog;
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

    /**
     * Requirement ke ek run ke har match ke gaps (Skill Growth screen). runId na diya ho to latest
     * finished run - /requirements/{id}/matches jaisa; runId se purana run (rematch se pehle ka) dekh sakte hain.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forRequirement(Integer requirementId, Integer runId) {
        ProjectRequirement req = queryService.findRequirement(requirementId);
        MatchingRun run = queryService.resolveRun(requirementId, runId);
        if (run == null) {
            return List.of();
        }
        List<MatchingResult> results = matchingResultRepository.findByMatchingRunIdOrderByRankPositionAsc(run.getMatchingRunId());
        if (results.isEmpty()) {
            return List.of();
        }
        List<Integer> resultIds = results.stream().map(MatchingResult::getMatchingResultId).toList();
        Context ctx = context(req);
        Map<Integer, Candidate> candidates = candidatePool.loadByIds(
                results.stream().map(MatchingResult::getEmployeeId).collect(Collectors.toSet()));
        Map<Integer, List<LearningRecommendation>> recs = learningRecommendationRepository
                .findByMatchingResultIdIn(resultIds)
                .stream().collect(Collectors.groupingBy(LearningRecommendation::getMatchingResultId));
        Map<Integer, List<SkillGap>> stored = skillGapRepository.findByMatchingResultIdIn(resultIds)
                .stream().collect(Collectors.groupingBy(SkillGap::getMatchingResultId));

        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchingResult mr : results) {
            Candidate c = candidates.get(mr.getEmployeeId());
            List<LearningRecommendation> r = recs.getOrDefault(mr.getMatchingResultId(), List.of());
            out.add(toMap(build(mr, c, ctx, stored.getOrDefault(mr.getMatchingResultId(), List.of()), r), r));
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
        var now = MatchingPersistenceService.nowIst();
        lr.setReviewStatus(value);
        lr.setReviewComments(comments == null || comments.isBlank() ? null : comments.trim());
        lr.setReviewedBy(userId);
        lr.setReviewedAt(now);
        // FR-GAP-06: pathway ka start / finish time. Pehli baar hi set hota hai, baad me badalta nahi.
        if ("IN_PROGRESS".equals(value) || "COMPLETED".equals(value)) {
            if (lr.getStartedAt() == null) {
                lr.setStartedAt(now);
            }
        }
        if ("COMPLETED".equals(value) && lr.getCompletedAt() == null) {
            lr.setCompletedAt(now);
        }
        lr = learningRecommendationRepository.save(lr);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("review_status", value);
        after.put("review_comments", lr.getReviewComments());
        audit.log(userId, AuditService.LEARNING_REVIEWED, "learning_recommendations", learningRecommendationId, old, after, null);

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
        List<GapView> open = data.gaps().stream()
                .filter(g -> g.skillId() != null && !covered.contains(g.skillId()))
                .toList();

        Map<String, Object> body = toMap(data, data.recs());
        if (open.isEmpty()) {
            body.put("note", data.gaps().isEmpty() ? "No skill gaps for this match"
                    : "Every gap already has a learning recommendation");
            return body;
        }

        // FR-GAP-05: pehle approved L&K resources. Jin gaps ko resource mil gayi unke liye AI call nahi hota.
        Map<Integer, List<LearningResource>> approved = catalog.approvedBySkill(
                open.stream().map(GapView::skillId).collect(Collectors.toSet()));
        List<GapView> fromCatalog = new ArrayList<>();
        List<GapView> needAi = new ArrayList<>();
        for (GapView v : open) {
            (LearningCatalogService.pick(approved.get(v.skillId()), v.current(), v.required()) != null
                    ? fromCatalog : needAi).add(v);
        }

        List<LearningItem> items = new ArrayList<>();
        Map<Integer, String> priorityBySkill = new HashMap<>();
        for (GapView v : fromCatalog) {
            priorityBySkill.put(v.skillId(), v.priority());
            // stand-in: persistence isko approved resource se replace kar deta hai
            items.add(scorer.templateLearning(v.toGap()));
        }

        String aiNote = null;
        if (!needAi.isEmpty()) {
            if (!engine.isLlmEnabled()) {
                aiNote = "AI is turned off (app.matching.use-llm=false); no recommendations generated for "
                        + needAi.size() + " gap(s) that have no approved L&K resource";
            } else {
                try {
                    items.addAll(aiItems(needAi, priorityBySkill));
                } catch (Exception ex) {
                    aiNote = GeminiLlmClient.describeFailure(ex) + "; no AI recommendations generated for "
                            + needAi.size() + " gap(s) that have no approved L&K resource, try again later";
                }
            }
        }
        if (items.isEmpty()) {
            body.put("note", aiNote != null ? aiNote : "No recommendations generated");
            return body;
        }

        Integer userId = currentUser.currentUserId();
        List<LearningRecommendation> saved = persistence.saveRecommendations(matchingResultId, items, priorityBySkill, userId);
        audit.log(userId, AuditService.LEARNING_GENERATED, "matching_results", matchingResultId,
                null, Map.of("created", saved.size()), null);

        List<LearningRecommendation> all = new ArrayList<>(data.recs());
        all.addAll(saved);
        body = toMap(data, all);
        long lk = saved.stream().filter(s -> MatchingPersistenceService.SOURCE_LK.equals(s.getRecommendationSource())).count();
        long ai = saved.stream().filter(s -> MatchingPersistenceService.SOURCE_AI.equals(s.getRecommendationSource())).count();
        long rules = saved.size() - lk - ai;
        StringBuilder note = new StringBuilder("Generated " + saved.size() + " learning recommendation(s): "
                + lk + " from the approved L&K repository, " + ai + " written by AI, " + rules + " from the rule-based template.");
        if (ai > 0 || rules > 0) {
            note.append(" AI and template suggestions must be reviewed by L&K against approved learning resources.");
        }
        if (aiNote != null) {
            note.append(' ').append(aiNote).append('.');
        }
        body.put("note", note.toString());
        return body;
    }

    /** Ek Gemini call un gaps ke liye jinke liye approved resource nahi. Fail ho to exception caller pakadta hai. */
    private List<LearningItem> aiItems(List<GapView> gaps, Map<Integer, String> priorityBySkill) {
        JsonNode reply = gemini.suggestLearning(gapPayload(gaps));
        Map<String, JsonNode> bySkill = new HashMap<>();
        for (JsonNode n : reply.path("learning_suggestions")) {
            String skill = n.path("skill").asString("");
            if (!skill.isBlank()) {
                bySkill.putIfAbsent(skill.trim().toLowerCase(), n);
            }
        }
        List<LearningItem> items = new ArrayList<>();
        for (GapView v : gaps) {
            Gap g = v.toGap();
            priorityBySkill.put(v.skillId(), v.priority());
            JsonNode n = bySkill.get(v.skillName().toLowerCase());
            String action = n == null ? "" : n.path("action").asString("");
            if (action.isBlank()) {
                items.add(scorer.templateLearning(g));
            } else {
                String resource = n.path("resource").asString("");
                items.add(new LearningItem(g.skill(), g.currentLevel(),
                        action.length() > 500 ? action.substring(0, 500) : action,
                        resource.isBlank() ? null : (resource.length() > 1000 ? resource.substring(0, 1000) : resource),
                        MatchScorer.resourceType(n.path("resource_type").asString(null)),
                        MatchScorer.gapDescription(g), true));
            }
        }
        return items;
    }

    // ---------------- helpers ----------------

    private GapData load(Integer matchingResultId) {
        MatchingResult mr = matchingResultRepository.findById(matchingResultId)
                .orElseThrow(() -> new NoSuchElementException("Matching result not found: " + matchingResultId));
        MatchingRun run = matchingRunRepository.findById(mr.getMatchingRunId())
                .orElseThrow(() -> new NoSuchElementException("Matching run not found: " + mr.getMatchingRunId()));
        Context ctx = context(queryService.findRequirement(run.getRequirementId()));
        Candidate c = candidatePool.loadByIds(Set.of(mr.getEmployeeId())).get(mr.getEmployeeId());
        return build(mr, c, ctx, skillGapRepository.findByMatchingResultId(matchingResultId),
                learningRecommendationRepository.findByMatchingResultId(matchingResultId));
    }

    private Context context(ProjectRequirement req) {
        Map<Integer, Skill> skillsById = candidatePool.skillsById();
        Spec spec = candidatePool.specFromRequirement(
                requiredSkillRepository.findByRequirementId(req.getRequirementId()),
                req.getMinimumExperienceMonths(), skillsById);
        return new Context(spec, skillsById, req.getRequirementPriority());
    }

    /**
     * Stored rows hon ya result ke JSON me koi gap hi na ho -> stored (khaali bhi ho sakta hai, "no gaps").
     * Rows nahi par JSON me gaps hain -> purana run, live calculation.
     */
    private GapData build(MatchingResult mr, Candidate c, Context ctx, List<SkillGap> storedRows,
                          List<LearningRecommendation> recs) {
        if (!storedRows.isEmpty() || !jsonHasGaps(mr.getSkillGaps())) {
            List<GapView> views = new ArrayList<>();
            for (SkillGap row : storedRows) {
                Skill skill = ctx.skillsById().get(row.getSkillId());
                views.add(view(row, skill != null ? skill.getSkillName() : "skill#" + row.getSkillId()));
            }
            return new GapData(mr, c, ctx, sorted(views), true, recs);
        }
        List<GapView> views = new ArrayList<>();
        if (c != null) {
            for (Gap g : scorer.score(ctx.spec(), c).gaps()) {
                SkillGap row = MatchingPersistenceService.toSkillGap(null, g, ctx.requirementPriority());
                if (row != null) {
                    views.add(view(row, g.skill().skillName()));
                }
            }
        }
        return new GapData(mr, c, ctx, sorted(views), false, recs);
    }

    private static GapView view(SkillGap row, String skillName) {
        return new GapView(row.getSkillGapId(), row.getSkillId(), skillName, row.getRequiredProficiency(),
                row.getCurrentProficiency(), Boolean.TRUE.equals(row.getIsMandatory()), row.getGapType(),
                row.getGapSize(), row.getPriority(), row.getPriorityReason());
    }

    /** Sabse urgent pehle, phir bada gap, phir naam. */
    private static List<GapView> sorted(List<GapView> views) {
        return views.stream()
                .sorted(Comparator.comparingInt((GapView v) -> GapPriorityRules.rank(v.priority()))
                        .thenComparing(Comparator.comparingInt(GapView::gapSize).reversed())
                        .thenComparing(v -> v.skillName() == null ? "" : v.skillName()))
                .toList();
    }

    static boolean jsonHasGaps(String raw) {
        JsonNode n = RequirementQueryService.parseJson(raw);
        return n != null && (n.path("missing_skills").size() > 0 || n.path("below_required_proficiency").size() > 0);
    }

    private Map<String, Object> toMap(GapData d, List<LearningRecommendation> recs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("matching_result_id", d.result().getMatchingResultId());
        m.put("matching_run_id", d.result().getMatchingRunId());
        m.put("employee_id", d.result().getEmployeeId());
        m.put("employee_code", d.candidate() != null ? d.candidate().employeeCode() : null);
        m.put("resource_name", d.candidate() != null ? d.candidate().name() : null);
        m.put("match_category", d.result().getMatchCategory());
        // STORED = skill_gaps table (run ke time ke gaps). LIVE_LEGACY = purana run, abhi ki skills se nikala.
        m.put("gaps_source", d.stored() ? "STORED" : "LIVE_LEGACY");

        List<Map<String, Object>> gaps = new ArrayList<>();
        for (GapView v : d.gaps()) {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("skill_gap_id", v.skillGapId());
            gm.put("skill_id", v.skillId());
            gm.put("skill_name", v.skillName());
            gm.put("gap_type", v.gapType());
            gm.put("required_level", v.required());
            gm.put("current_level", v.current());
            gm.put("gap_size", v.gapSize());
            gm.put("mandatory", v.mandatory());
            gm.put("priority", v.priority());
            gm.put("priority_reason", v.reason());
            gm.put("gap_description", MatchScorer.gapDescription(v.toGap()));
            gaps.add(gm);
        }
        m.put("gaps", gaps);
        m.put("learning_recommendations", RequirementQueryService.learningMaps(recs, d.ctx().skillsById()));
        return m;
    }

    private List<Map<String, Object>> gapPayload(List<GapView> gaps) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GapView g : gaps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skill", g.skillName());
            m.put("current_level", g.current());
            m.put("target_level", g.required());
            m.put("mandatory", g.mandatory());
            out.add(m);
        }
        return out;
    }
}
