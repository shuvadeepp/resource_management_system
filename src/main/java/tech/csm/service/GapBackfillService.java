package tech.csm.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.model.LearningRecommendation;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.RequiredSkill;
import tech.csm.model.Skill;
import tech.csm.model.SkillGap;
import tech.csm.repository.LearningRecommendationRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
import tech.csm.repository.ProjectRequirementRepository;
import tech.csm.repository.RequiredSkillRepository;
import tech.csm.repository.SkillGapRepository;
import tech.csm.service.MatchingData.Gap;
import tech.csm.service.MatchingData.ReqSkill;
import tools.jackson.databind.JsonNode;

/**
 * Stage 1 se pehle ke runs ke liye skill_gaps rows banata hai. Source matching_results.skill_gaps JSON hai -
 * wo run ke time ka snapshot hai (missing skills + has/needs levels), isliye employees ki abhi ki skills par
 * depend nahi. Jis result ki rows pehle se hain use chhoda jaata hai (dobara chalana safe hai). Purani
 * recommendations jinka skill_gap_id null hai, unhe bhi unke gap se jod diya jaata hai.
 */
@Service
public class GapBackfillService {

    public record Parsed(List<Gap> gaps, int unknownSkill, int notInRequirement) {
    }

    private final MatchingRunRepository matchingRunRepository;
    private final MatchingResultRepository matchingResultRepository;
    private final SkillGapRepository skillGapRepository;
    private final LearningRecommendationRepository learningRecommendationRepository;
    private final RequiredSkillRepository requiredSkillRepository;
    private final ProjectRequirementRepository requirementRepository;
    private final CandidatePoolService candidatePool;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public GapBackfillService(MatchingRunRepository matchingRunRepository,
                              MatchingResultRepository matchingResultRepository,
                              SkillGapRepository skillGapRepository,
                              LearningRecommendationRepository learningRecommendationRepository,
                              RequiredSkillRepository requiredSkillRepository,
                              ProjectRequirementRepository requirementRepository,
                              CandidatePoolService candidatePool,
                              CurrentUserService currentUser,
                              AuditService audit) {
        this.matchingRunRepository = matchingRunRepository;
        this.matchingResultRepository = matchingResultRepository;
        this.skillGapRepository = skillGapRepository;
        this.learningRecommendationRepository = learningRecommendationRepository;
        this.requiredSkillRepository = requiredSkillRepository;
        this.requirementRepository = requirementRepository;
        this.candidatePool = candidatePool;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /** runId > requirementId > sab finished runs. dryRun = true: sirf ginti, kuch likhta nahi. */
    @Transactional
    public Map<String, Object> backfill(Integer requirementId, Integer runId, boolean dryRun) {
        List<MatchingRun> runs;
        if (runId != null) {
            runs = List.of(matchingRunRepository.findById(runId)
                    .orElseThrow(() -> new NoSuchElementException("Matching run not found: " + runId)));
        } else if (requirementId != null) {
            runs = matchingRunRepository.findByRequirementIdAndRunStatusInOrderByMatchingRunIdDesc(
                    requirementId, RequirementQueryService.FINISHED);
        } else {
            runs = matchingRunRepository.findByRunStatusIn(RequirementQueryService.FINISHED);
        }

        Map<String, Skill> skillsByName = candidatePool.skillsByName();
        Map<Integer, Map<Integer, RequiredSkill>> requiredByRequirement = new HashMap<>();
        Map<Integer, String> priorityByRequirement = new HashMap<>();

        int resultsScanned = 0;
        int alreadyFilled = 0;
        int noGaps = 0;
        int resultsFilled = 0;
        int gapsToInsert = 0;
        int linked = 0;
        int unknownSkill = 0;
        int notInRequirement = 0;

        for (MatchingRun run : runs) {
            Integer reqId = run.getRequirementId();
            Map<Integer, RequiredSkill> required = requiredByRequirement.computeIfAbsent(reqId, id ->
                    requiredSkillRepository.findByRequirementId(id).stream()
                            .collect(Collectors.toMap(RequiredSkill::getSkillId, r -> r, (a, b) -> a)));
            String reqPriority = priorityByRequirement.computeIfAbsent(reqId, id ->
                    requirementRepository.findById(id).map(r -> r.getRequirementPriority()).orElse("MEDIUM"));

            List<MatchingResult> results = matchingResultRepository.findByMatchingRunIdIn(List.of(run.getMatchingRunId()));
            if (results.isEmpty()) {
                continue;
            }
            List<Integer> ids = results.stream().map(MatchingResult::getMatchingResultId).toList();
            Map<Integer, List<SkillGap>> existing = skillGapRepository.findByMatchingResultIdIn(ids).stream()
                    .collect(Collectors.groupingBy(SkillGap::getMatchingResultId));
            Map<Integer, List<LearningRecommendation>> recs = learningRecommendationRepository
                    .findByMatchingResultIdIn(ids).stream()
                    .collect(Collectors.groupingBy(LearningRecommendation::getMatchingResultId));

            for (MatchingResult mr : results) {
                resultsScanned++;
                if (existing.containsKey(mr.getMatchingResultId())) {
                    alreadyFilled++;
                    continue;
                }
                JsonNode json = RequirementQueryService.parseJson(mr.getSkillGaps());
                if (json == null || !SkillGapService.jsonHasGaps(mr.getSkillGaps())) {
                    noGaps++;
                    continue;
                }
                Parsed parsed = parse(json, skillsByName, required);
                unknownSkill += parsed.unknownSkill();
                notInRequirement += parsed.notInRequirement();
                List<SkillGap> rows = MatchingPersistenceService.toSkillGaps(mr.getMatchingResultId(), parsed.gaps(), reqPriority);
                if (rows.isEmpty()) {
                    continue;
                }
                resultsFilled++;
                gapsToInsert += rows.size();
                if (dryRun) {
                    continue;
                }
                List<SkillGap> saved = skillGapRepository.saveAll(rows);
                Map<Integer, SkillGap> bySkill = saved.stream()
                        .collect(Collectors.toMap(SkillGap::getSkillId, g -> g, (a, b) -> a));
                List<LearningRecommendation> toLink = new ArrayList<>();
                for (LearningRecommendation lr : recs.getOrDefault(mr.getMatchingResultId(), List.of())) {
                    SkillGap g = lr.getSkillGapId() == null ? bySkill.get(lr.getSkillId()) : null;
                    if (g != null) {
                        lr.setSkillGapId(g.getSkillGapId());
                        lr.setPriority(g.getPriority());
                        toLink.add(lr);
                    }
                }
                learningRecommendationRepository.saveAll(toLink);
                linked += toLink.size();
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dry_run", dryRun);
        body.put("runs_scanned", runs.size());
        body.put("results_scanned", resultsScanned);
        body.put("results_already_have_gaps", alreadyFilled);
        body.put("results_with_no_gaps_in_json", noGaps);
        body.put("results_filled", resultsFilled);
        body.put(dryRun ? "gaps_that_would_be_inserted" : "gaps_inserted", gapsToInsert);
        body.put("recommendations_linked", dryRun ? 0 : linked);
        body.put("skipped_skill_not_in_master", unknownSkill);
        body.put("skipped_missing_skill_not_in_requirement", notInRequirement);
        body.put("note", dryRun
                ? "Nothing was written. Call again with dryRun=false to write these rows."
                : "Rows were written from each run's saved JSON snapshot. Safe to run again: results that already have gaps are skipped.");
        if (!dryRun && gapsToInsert > 0) {
            audit.log(currentUser.currentUserId(), AuditService.GAPS_BACKFILLED, "skill_gaps",
                    runs.isEmpty() ? 0 : runs.get(0).getMatchingRunId(), null, body, null);
        }
        return body;
    }

    /**
     * matching_results.skill_gaps JSON se gaps. missing_skills: sirf naam, required level requirement se.
     * below_required_proficiency: {skill, has, needs} - snapshot se seedha. Mandatory flag requirement ki
     * required_skills se (row na mile to preferred maana jaata hai).
     */
    static Parsed parse(JsonNode json, Map<String, Skill> skillsByName, Map<Integer, RequiredSkill> required) {
        List<Gap> gaps = new ArrayList<>();
        int unknown = 0;
        int notInReq = 0;
        for (JsonNode n : json.path("missing_skills")) {
            Skill s = skillsByName.get(n.asString("").trim().toLowerCase());
            if (s == null) {
                unknown++;
                continue;
            }
            RequiredSkill rs = required.get(s.getSkillId());
            if (rs == null) {
                notInReq++;
                continue;
            }
            int min = rs.getMinimumProficiency() != null ? rs.getMinimumProficiency() : 1;
            gaps.add(new Gap(new ReqSkill(s.getSkillId(), s.getSkillName(), null,
                    Boolean.TRUE.equals(rs.getIsMandatory()), min), null));
        }
        for (JsonNode n : json.path("below_required_proficiency")) {
            Skill s = skillsByName.get(n.path("skill").asString("").trim().toLowerCase());
            if (s == null) {
                unknown++;
                continue;
            }
            RequiredSkill rs = required.get(s.getSkillId());
            int needs = n.path("needs").asInt(0);
            int has = n.path("has").asInt(0);
            if (needs < 1) {
                needs = rs != null && rs.getMinimumProficiency() != null ? rs.getMinimumProficiency() : 1;
            }
            gaps.add(new Gap(new ReqSkill(s.getSkillId(), s.getSkillName(), null,
                    rs != null && Boolean.TRUE.equals(rs.getIsMandatory()), needs), has));
        }
        return new Parsed(gaps, unknown, notInReq);
    }
}

