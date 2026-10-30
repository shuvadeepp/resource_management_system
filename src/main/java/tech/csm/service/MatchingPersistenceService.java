package tech.csm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.model.LearningRecommendation;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.repository.LearningRecommendationRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
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

    private static final Logger log = LoggerFactory.getLogger(MatchingPersistenceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SCORING_VERSION = "frd-weights-v1";

    private final MatchingRunRepository matchingRunRepository;
    private final MatchingResultRepository matchingResultRepository;
    private final LearningRecommendationRepository learningRecommendationRepository;

    @Value("${gemini.model}")
    private String geminiModel;

    @Value("${app.matching.use-llm:true}")
    private boolean useLlm;

    public MatchingPersistenceService(MatchingRunRepository matchingRunRepository,
                                      MatchingResultRepository matchingResultRepository,
                                      LearningRecommendationRepository learningRecommendationRepository) {
        this.matchingRunRepository = matchingRunRepository;
        this.matchingResultRepository = matchingResultRepository;
        this.learningRecommendationRepository = learningRecommendationRepository;
    }

    @Transactional
    public MatchingRun createRun(Integer requirementId, int candidatePoolSize, Integer executedBy) {
        MatchingRun run = new MatchingRun();
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
     * Saare results ek saveAll me, phir unki learning recommendations ek saveAll me,
     * phir run COMPLETED / COMPLETED_WITH_ERRORS - sab ek transaction.
     * Returned list outcome.matches() ke order me hai.
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

        List<LearningRecommendation> recs = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            recs.addAll(toRecommendations(results.get(i).getMatchingResultId(), outcome.matches().get(i).learning()));
        }
        learningRecommendationRepository.saveAll(recs);

        MatchingRun run = find(runId);
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

    /** On-demand generate se aayi recommendations. */
    @Transactional
    public List<LearningRecommendation> saveRecommendations(Integer matchingResultId, List<LearningItem> items) {
        return learningRecommendationRepository.saveAll(toRecommendations(matchingResultId, items));
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

    private List<LearningRecommendation> toRecommendations(Integer matchingResultId, List<LearningItem> items) {
        List<LearningRecommendation> out = new ArrayList<>();
        for (LearningItem li : items) {
            if (li.skill().skillId() == null) {
                log.warn("Skipping learning recommendation: skill '{}' not in skill master", li.skill().skillName());
                continue;
            }
            LearningRecommendation lr = new LearningRecommendation();
            lr.setMatchingResultId(matchingResultId);
            lr.setSkillId(li.skill().skillId());
            lr.setCurrentProficiency(li.currentLevel());
            lr.setTargetProficiency(li.skill().minProficiency());
            lr.setSkillGapDescription(li.gapDescription());
            lr.setLearningAction(li.action());
            lr.setLearningResource(li.resource());
            lr.setResourceType(MatchScorer.resourceType(li.resourceType()));
            lr.setReviewStatus("PENDING");
            out.add(lr);
        }
        return out;
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
