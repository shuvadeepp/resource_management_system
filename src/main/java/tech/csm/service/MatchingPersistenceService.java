package tech.csm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.model.LearningRecommendation;
import tech.csm.model.LearningResource;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.ProjectRequirement;
import tech.csm.model.SkillGap;
import tech.csm.repository.LearningRecommendationRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
import tech.csm.repository.ProjectRequirementRepository;
import tech.csm.repository.SkillGapRepository;
import tech.csm.service.MatchingData.FinalMatch;
import tech.csm.service.MatchingData.Gap;
import tech.csm.service.MatchingData.LearningItem;
import tech.csm.service.MatchingData.Outcome;
import tech.csm.service.MatchingData.Transferable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * matching_runs / matching_results / learning_recommendations ke saare writes.
 * Har method ek chhoti transaction hai - Gemini call kabhi inke andar nahi hota.
 */
@Service
public class MatchingPersistenceService {

    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String COMPLETED_WITH_ERRORS = "COMPLETED_WITH_ERRORS";
    public static final String FAILED = "FAILED";

    public static final String NEW_SEARCH = "NEW_SEARCH";
    public static final String REMATCH_AFTER_LEARNING = "REMATCH_AFTER_LEARNING";
    public static final String MANUAL_REMATCH = "MANUAL_REMATCH";

    public static final String SOURCE_LK = "LK_REPOSITORY";
    public static final String SOURCE_AI = "AI_GENERATED";
    public static final String SOURCE_RULES = "RULE_BASED";

    private static final Logger log = LoggerFactory.getLogger(MatchingPersistenceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SCORING_VERSION = "frd-weights-v1";

    private final MatchingRunRepository matchingRunRepository;
    private final MatchingResultRepository matchingResultRepository;
    private final LearningRecommendationRepository learningRecommendationRepository;
    private final SkillGapRepository skillGapRepository;
    private final ProjectRequirementRepository requirementRepository;
    private final LearningCatalogService catalog;

    @Value("${gemini.model}")
    private String geminiModel;

    @Value("${app.matching.use-llm:true}")
    private boolean useLlm;

    public MatchingPersistenceService(MatchingRunRepository matchingRunRepository,
                                      MatchingResultRepository matchingResultRepository,
                                      LearningRecommendationRepository learningRecommendationRepository,
                                      SkillGapRepository skillGapRepository,
                                      ProjectRequirementRepository requirementRepository,
                                      LearningCatalogService catalog) {
        this.matchingRunRepository = matchingRunRepository;
        this.matchingResultRepository = matchingResultRepository;
        this.learningRecommendationRepository = learningRecommendationRepository;
        this.skillGapRepository = skillGapRepository;
        this.requirementRepository = requirementRepository;
        this.catalog = catalog;
    }

    @Transactional
    public MatchingRun createRun(Integer requirementId, int candidatePoolSize, Integer executedBy) {
        return createRun(requirementId, candidatePoolSize, executedBy, NEW_SEARCH, null, null);
    }

    /** Rematch (FR-GAP-08): previousRunId purane run ko point karta hai; purane results touch nahi hote. */
    @Transactional
    public MatchingRun createRun(Integer requirementId, int candidatePoolSize, Integer executedBy,
                                 String runTrigger, Integer previousRunId, String rematchNote) {
        MatchingRun run = new MatchingRun();
        run.setRunTrigger(runTrigger);
        run.setPreviousRunId(previousRunId);
        run.setRematchNote(rematchNote);
        run.setRequirementId(requirementId);
        run.setScoringConfigurationVersion(SCORING_VERSION);
        run.setAlgorithmName(useLlm ? "GEMINI" : "RULES");
        run.setAlgorithmVersion("v1");
        run.setModelVersion(useLlm ? geminiModel : null);
        run.setCandidatePoolSize(candidatePoolSize);
        run.setRunStatus(QUEUED);
        run.setExecutedBy(executedBy);
        return matchingRunRepository.save(run);
    }

    @Transactional
    public void markRunning(Integer runId) {
        MatchingRun run = find(runId);
        run.setRunStatus(RUNNING);
        matchingRunRepository.save(run);
    }

    /**
     * Saare results ek saveAll me, phir unke skill_gaps (Java rules se priority), phir learning
     * recommendations (har ek apne gap se linked), phir run COMPLETED / COMPLETED_WITH_ERRORS -
     * sab ek transaction. Returned list outcome.matches() ke order me hai.
     */
    @Transactional
    public List<MatchingResult> saveOutcome(Integer runId, Outcome outcome) {
        List<MatchingResult> results = new ArrayList<>();
        for (FinalMatch fm : outcome.matches()) {
            MatchingResult mr = new MatchingResult();
            mr.setMatchingRunId(runId);
            mr.setEmployeeId(fm.scored().candidate().employeeId());
            mr.setOverallScore(fm.score());
            mr.setMatchCategory(fm.category());
            mr.setRankPosition(fm.rank());
            mr.setExplanation(fm.explanation());
            mr.setRecommendationStatus("REVIEW_REQUIRED");
            mr.setStrengths(strengthsJson(fm));
            mr.setSkillGaps(gapsJson(fm.scored().gaps()));
            results.add(mr);
        }
        results = matchingResultRepository.saveAll(results);

        MatchingRun run = find(runId);
        String requirementPriority = requirementRepository.findById(run.getRequirementId())
                .map(ProjectRequirement::getRequirementPriority).orElse(null);

        List<SkillGap> gapRows = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            gapRows.addAll(toSkillGaps(results.get(i).getMatchingResultId(),
                    outcome.matches().get(i).scored().gaps(), requirementPriority));
        }
        Map<Integer, Map<Integer, SkillGap>> gapsByResult = new HashMap<>();
        for (SkillGap sg : skillGapRepository.saveAll(gapRows)) {
            gapsByResult.computeIfAbsent(sg.getMatchingResultId(), k -> new HashMap<>()).put(sg.getSkillId(), sg);
        }

        // FR-GAP-05: approved L&K resources pehle - ek query poore run ke liye
        Set<Integer> learningSkillIds = outcome.matches().stream()
                .flatMap(fm -> fm.learning().stream())
                .map(li -> li.skill().skillId()).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, List<LearningResource>> approved = catalog.approvedBySkill(learningSkillIds);

        List<LearningRecommendation> recs = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            Integer resultId = results.get(i).getMatchingResultId();
            recs.addAll(toRecommendations(resultId, outcome.matches().get(i).learning(),
                    gapsByResult.getOrDefault(resultId, Map.of()), Map.of(), approved, run.getExecutedBy(), 0));
        }
        learningRecommendationRepository.saveAll(recs);

        run.setRunStatus(MatchingData.AI_FAILED.equals(outcome.aiStatus()) ? COMPLETED_WITH_ERRORS : COMPLETED);
        run.setErrorMessage(outcome.aiError());
        if (!MatchingData.AI_USED.equals(outcome.aiStatus())) {
            run.setAlgorithmName("RULES");
        }
        run.setCandidatePoolSize(outcome.poolSize());
        run.setCompletedAt(MatchingPersistenceService.nowIst());
        matchingRunRepository.save(run);
        return results;
    }

    @Transactional
    public void markFailed(Integer runId, String message) {
        MatchingRun run = find(runId);
        run.setRunStatus(FAILED);
        run.setErrorMessage(message);
        run.setCompletedAt(MatchingPersistenceService.nowIst());
        matchingRunRepository.save(run);
    }

    /**
     * Startup par: jo runs QUEUED/RUNNING me atke hain unhe FAILED. App ek hi
     * instance me chalti hai, isliye restart ke baad inme se koi zinda nahi ho sakta.
     */
    @Transactional
    public int recoverInterruptedRuns() {
        List<MatchingRun> stuck = matchingRunRepository.findByRunStatusIn(List.of(QUEUED, RUNNING));
        for (MatchingRun run : stuck) {
            run.setRunStatus(FAILED);
            run.setErrorMessage("Interrupted by a server restart before it finished");
            run.setCompletedAt(MatchingPersistenceService.nowIst());
        }
        matchingRunRepository.saveAll(stuck);
        return stuck.size();
    }

    /**
     * On-demand generate se aayi recommendations. Stored gap mile to link hota hai; purane runs
     * (jinke skill_gaps rows nahi) ke liye skill_gap_id null aur priority fallbackPriority se aati hai.
     */
    @Transactional
    public List<LearningRecommendation> saveRecommendations(Integer matchingResultId, List<LearningItem> items,
                                                            Map<Integer, String> fallbackPriority, Integer createdBy) {
        Map<Integer, SkillGap> gapsBySkill = skillGapRepository.findByMatchingResultId(matchingResultId).stream()
                .collect(Collectors.toMap(SkillGap::getSkillId, Function.identity(), (a, b) -> a));
        int existing = learningRecommendationRepository.findByMatchingResultId(matchingResultId).stream()
                .mapToInt(lr -> lr.getSequenceOrder() == null ? 0 : lr.getSequenceOrder()).max().orElse(0);
        Map<Integer, List<LearningResource>> approved = catalog.approvedBySkill(items.stream()
                .map(li -> li.skill().skillId()).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return learningRecommendationRepository.saveAll(
                toRecommendations(matchingResultId, items, gapsBySkill, fallbackPriority, approved, createdBy, existing));
    }

    // ---------------- helpers ----------------

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * App India time par chalti hai: DB session time_zone = Asia/Kolkata (CURRENT_TIMESTAMP
     * defaults IST), isliye Java ki taraf se likhe timestamps (completed_at, reviewed_at) bhi IST.
     */
    public static LocalDateTime nowIst() {
        return LocalDateTime.now(IST);
    }

    private MatchingRun find(Integer runId) {
        return matchingRunRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Matching run not found: " + runId));
    }

    /**
     * Har item ek recommendation. Source saaf rakhta hai: Gemini ka text AI_GENERATED, Java ka template
     * RULE_BASED - L&K reviewer ko dikhna chahiye kya AI se aaya (FRD Module 5). Ek result ke saare
     * recommendations priority ke hisaab se sequence_order (startSequence ke baad se) pate hain.
     */
    private List<LearningRecommendation> toRecommendations(Integer matchingResultId, List<LearningItem> items,
                                                           Map<Integer, SkillGap> gapsBySkill,
                                                           Map<Integer, String> fallbackPriority,
                                                           Map<Integer, List<LearningResource>> approved,
                                                           Integer createdBy, int startSequence) {
        List<LearningRecommendation> out = new ArrayList<>();
        for (LearningItem li : items) {
            Integer skillId = li.skill().skillId();
            if (skillId == null) {
                log.warn("Skipping learning recommendation: skill '{}' not in skill master", li.skill().skillName());
                continue;
            }
            SkillGap gap = gapsBySkill.get(skillId);
            String resourceType = MatchScorer.resourceType(li.resourceType());

            LearningRecommendation lr = new LearningRecommendation();
            lr.setMatchingResultId(matchingResultId);
            lr.setSkillId(skillId);
            lr.setCurrentProficiency(li.currentLevel());
            lr.setTargetProficiency(li.skill().minProficiency());
            lr.setSkillGapDescription(li.gapDescription());
            lr.setLearningAction(li.action());
            lr.setLearningResource(li.resource());
            lr.setResourceType(resourceType);
            lr.setReviewStatus("PENDING");
            lr.setSkillGapId(gap != null ? gap.getSkillGapId() : null);
            lr.setRecommendationSource(li.fromAi() ? SOURCE_AI : SOURCE_RULES);
            lr.setAiModelVersion(li.fromAi() && useLlm ? geminiModel : null);
            lr.setPriority(gap != null ? gap.getPriority() : fallbackPriority.getOrDefault(skillId, "MEDIUM"));
            lr.setValidationMethod(validationFor(resourceType));
            lr.setCreatedBy(createdBy);

            // Approved L&K resource mile to wahi use hoti hai - AI/template ka text uski jagah nahi jaata.
            LearningResource res = LearningCatalogService.pick(approved.get(skillId), li.currentLevel(),
                    li.skill().minProficiency());
            if (res != null) {
                applyCatalog(lr, res);
            }
            out.add(lr);
        }
        out.sort(Comparator.comparingInt(lr -> GapPriorityRules.rank(lr.getPriority())));
        int seq = startSequence;
        for (LearningRecommendation lr : out) {
            lr.setSequenceOrder(Math.min(255, ++seq)); // sequence_order tinyint unsigned
        }
        return out;
    }

    /** Recommendation ko L&K repository ki resource se bharo: source LK_REPOSITORY, hours/weeks/validation resource se. */
    private void applyCatalog(LearningRecommendation lr, LearningResource res) {
        String action = "Complete the approved L&K learning resource: " + res.getResourceTitle();
        lr.setLearningAction(action.length() > 500 ? action.substring(0, 500) : action);
        String where = res.getResourceUrl() != null ? res.getResourceUrl()
                : (res.getProvider() != null ? res.getProvider() + ": " + res.getResourceTitle() : res.getResourceTitle());
        lr.setLearningResource(where.length() > 1000 ? where.substring(0, 1000) : where);
        lr.setResourceType(MatchScorer.resourceType(res.getResourceType()));
        lr.setLearningResourceId(res.getLearningResourceId());
        lr.setRecommendationSource(SOURCE_LK);
        lr.setAiModelVersion(null);
        lr.setEstimatedHours(res.getEstimatedHours());
        lr.setEstimatedWeeks(catalog.weeksFor(res.getEstimatedHours()));
        lr.setValidationMethod(res.getValidationMethod() != null ? res.getValidationMethod() : lr.getValidationMethod());
    }

    static String validationFor(String resourceType) {
        return switch (resourceType) {
            case "PRACTICAL_ASSIGNMENT" -> "PRACTICAL_ASSIGNMENT";
            case "CERTIFICATION" -> "CERTIFICATE";
            default -> "KNOWLEDGE_ASSESSMENT";
        };
    }

    // ---------------- skill_gaps ----------------

    /** Ek result ke gaps -> rows. Ek skill ki do rows nahi (uq_gap_result_skill), master me na ho to skip. */
    static List<SkillGap> toSkillGaps(Integer matchingResultId, List<Gap> gaps, String requirementPriority) {
        Map<Integer, SkillGap> bySkill = new LinkedHashMap<>();
        for (Gap g : gaps) {
            Integer skillId = g.skill().skillId();
            if (skillId == null || bySkill.containsKey(skillId)) {
                continue;
            }
            SkillGap row = toSkillGap(matchingResultId, g, requirementPriority);
            if (row != null) {
                bySkill.put(skillId, row);
            }
        }
        return new ArrayList<>(bySkill.values());
    }

    /**
     * DB CHECK constraints ke andar rehne ke liye levels 1..5 me. Skill listed hai par rating nahi
     * (level 0) to MISSING store hota hai, reason me likha jaata hai. Gap na ho to null.
     */
    static SkillGap toSkillGap(Integer matchingResultId, Gap g, String requirementPriority) {
        int required = Math.max(1, Math.min(5, g.skill().minProficiency()));
        Integer current = g.currentLevel();
        boolean unrated = current != null && current < 1;
        boolean missing = current == null || unrated;
        if (!missing && current >= required) {
            return null;
        }
        GapPriorityRules.Result p = GapPriorityRules.priorityFor(missing, g.skill().mandatory(), requirementPriority);

        SkillGap row = new SkillGap();
        row.setMatchingResultId(matchingResultId);
        row.setSkillId(g.skill().skillId());
        row.setGapType(missing ? SkillGap.MISSING : SkillGap.BELOW_REQUIRED);
        row.setIsMandatory(g.skill().mandatory());
        row.setRequiredProficiency(required);
        row.setCurrentProficiency(missing ? null : current);
        row.setGapSize(Math.max(1, Math.min(5, required - (missing ? 0 : current))));
        row.setPriority(p.priority());
        String reason = unrated ? p.reason() + "; skill is listed but has no rating" : p.reason();
        row.setPriorityReason(reason.length() > 250 ? reason.substring(0, 250) : reason);
        return row;
    }

    private String strengthsJson(FinalMatch fm) {
        ObjectNode n = MAPPER.createObjectNode();
        ArrayNode matching = n.putArray("matching_skills");
        fm.scored().matchingSkills().forEach(matching::add);
        ArrayNode transferable = n.putArray("transferable_skills");
        for (Transferable t : fm.scored().transferable()) {
            ObjectNode tn = transferable.addObject();
            tn.put("skill", t.skill());
            tn.put("related_to", t.relatedTo());
            tn.put("note", "transferable, needs validation");
        }
        n.put("scored_by", fm.scoredBy());
        n.put("rule_score", fm.scored().ruleScore());
        return n.toString();
    }

    public static String gapsJson(List<Gap> gaps) {
        ObjectNode n = MAPPER.createObjectNode();
        ArrayNode missing = n.putArray("missing_skills");
        ArrayNode below = n.putArray("below_required_proficiency");
        for (Gap g : gaps) {
            if (g.missing()) {
                missing.add(g.skill().skillName());
            } else {
                ObjectNode b = below.addObject();
                b.put("skill", g.skill().skillName());
                b.put("has", g.currentLevel());
                b.put("needs", g.skill().minProficiency());
            }
        }
        return n.toString();
    }
}
