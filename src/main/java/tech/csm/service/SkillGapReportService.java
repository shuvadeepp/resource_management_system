package tech.csm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.exception.ApiException;
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
import tech.csm.repository.SkillGapRepository;
import tech.csm.service.MatchingData.Candidate;

/**
 * Module 5 ke read-only reports: gap list / summary (FR-GAP-01..03), gap ke liye approved resources
 * (FR-GAP-05) aur learning pathway (FR-GAP-06). Sab stored skill_gaps / learning_recommendations se.
 */
@Service
public class SkillGapReportService {

    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> GAP_TYPES = Set.of(SkillGap.MISSING, SkillGap.BELOW_REQUIRED);
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;

    /** requirementId / runId / employeeId me se kuch na ho = har requirement ka latest finished run. */
    public record GapFilter(Integer requirementId, Integer runId, Integer employeeId, String priority,
                            String gapType, Boolean mandatory, Integer skillId, Boolean hasRecommendation,
                            Integer limit) {
    }

    private record Scope(List<MatchingResult> results, Map<Integer, MatchingResult> byId,
                         Map<Integer, MatchingRun> runsById, List<SkillGap> gaps) {
    }

    private final SkillGapRepository skillGapRepository;
    private final MatchingResultRepository matchingResultRepository;
    private final MatchingRunRepository matchingRunRepository;
    private final LearningRecommendationRepository learningRecommendationRepository;
    private final RequirementQueryService queryService;
    private final CandidatePoolService candidatePool;
    private final LearningCatalogService catalog;

    public SkillGapReportService(SkillGapRepository skillGapRepository,
                                 MatchingResultRepository matchingResultRepository,
                                 MatchingRunRepository matchingRunRepository,
                                 LearningRecommendationRepository learningRecommendationRepository,
                                 RequirementQueryService queryService,
                                 CandidatePoolService candidatePool,
                                 LearningCatalogService catalog) {
        this.skillGapRepository = skillGapRepository;
        this.matchingResultRepository = matchingResultRepository;
        this.matchingRunRepository = matchingRunRepository;
        this.learningRecommendationRepository = learningRecommendationRepository;
        this.queryService = queryService;
        this.candidatePool = candidatePool;
        this.catalog = catalog;
    }

    // ---------------- gap list ----------------

    @Transactional(readOnly = true)
    public Map<String, Object> list(GapFilter f) {
        validate(f);
        Scope scope = scope(f);
        Map<Integer, List<LearningRecommendation>> recsByGap = recsByGap(scope);
        Map<Integer, Skill> skills = candidatePool.skillsById();
        Map<Integer, Candidate> people = candidatePool.loadByIds(
                scope.results().stream().map(MatchingResult::getEmployeeId).collect(Collectors.toSet()));

        List<SkillGap> gaps = filtered(scope, f, recsByGap);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SkillGap g : gaps) {
            MatchingResult mr = scope.byId().get(g.getMatchingResultId());
            MatchingRun run = scope.runsById().get(mr.getMatchingRunId());
            Candidate c = people.get(mr.getEmployeeId());
            Skill skill = skills.get(g.getSkillId());
            List<LearningRecommendation> recs = recsByGap.getOrDefault(g.getSkillGapId(), List.of());

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skill_gap_id", g.getSkillGapId());
            m.put("matching_result_id", g.getMatchingResultId());
            m.put("matching_run_id", mr.getMatchingRunId());
            m.put("requirement_id", run != null ? run.getRequirementId() : null);
            m.put("employee_id", mr.getEmployeeId());
            m.put("employee_code", c != null ? c.employeeCode() : null);
            m.put("resource_name", c != null ? c.name() : null);
            m.put("skill_id", g.getSkillId());
            m.put("skill_name", skill != null ? skill.getSkillName() : null);
            m.put("gap_type", g.getGapType());
            m.put("mandatory", g.getIsMandatory());
            m.put("required_level", g.getRequiredProficiency());
            m.put("current_level", g.getCurrentProficiency());
            m.put("gap_size", g.getGapSize());
            m.put("priority", g.getPriority());
            m.put("priority_reason", g.getPriorityReason());
            m.put("learning_recommendation_count", recs.size());
            m.put("recommendation_sources", recs.stream().map(LearningRecommendation::getRecommendationSource)
                    .distinct().sorted().toList());
            rows.add(m);
        }
        rows.sort(Comparator
                .comparingInt((Map<String, Object> m) -> GapPriorityRules.rank((String) m.get("priority")))
                .thenComparing((Map<String, Object> m) -> -(Integer) m.get("gap_size"))
                .thenComparing(m -> String.valueOf(m.get("resource_name")))
                .thenComparing(m -> String.valueOf(m.get("skill_name"))));

        int limit = limit(f);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", rows.size());
        body.put("returned", Math.min(limit, rows.size()));
        body.put("gaps", rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows);
        return body;
    }

    // ---------------- summary ----------------

    @Transactional(readOnly = true)
    public Map<String, Object> summary(GapFilter f, int topN) {
        validate(f);
        Scope scope = scope(f);
        Map<Integer, List<LearningRecommendation>> recsByGap = recsByGap(scope);
        List<SkillGap> gaps = filtered(scope, f, recsByGap);
        Map<Integer, Skill> skills = candidatePool.skillsById();

        Map<String, Integer> byPriority = new LinkedHashMap<>();
        for (String p : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW")) {
            byPriority.put(p, 0);
        }
        Map<String, Integer> byType = new LinkedHashMap<>();
        byType.put(SkillGap.MISSING, 0);
        byType.put(SkillGap.BELOW_REQUIRED, 0);
        int mandatory = 0;
        int noRecommendation = 0;
        Set<Integer> employees = new java.util.HashSet<>();
        Map<Integer, List<SkillGap>> bySkill = new HashMap<>();
        for (SkillGap g : gaps) {
            byPriority.merge(g.getPriority(), 1, Integer::sum);
            byType.merge(g.getGapType(), 1, Integer::sum);
            if (Boolean.TRUE.equals(g.getIsMandatory())) {
                mandatory++;
            }
            if (!recsByGap.containsKey(g.getSkillGapId())) {
                noRecommendation++;
            }
            employees.add(scope.byId().get(g.getMatchingResultId()).getEmployeeId());
            bySkill.computeIfAbsent(g.getSkillId(), k -> new ArrayList<>()).add(g);
        }

        List<Map<String, Object>> top = new ArrayList<>();
        bySkill.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(Math.max(1, topN))
                .forEach(e -> {
                    List<SkillGap> list = e.getValue();
                    Skill skill = skills.get(e.getKey());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("skill_id", e.getKey());
                    m.put("skill_name", skill != null ? skill.getSkillName() : null);
                    m.put("gap_count", list.size());
                    m.put("missing_count", list.stream().filter(g -> SkillGap.MISSING.equals(g.getGapType())).count());
                    m.put("below_required_count", list.stream().filter(g -> SkillGap.BELOW_REQUIRED.equals(g.getGapType())).count());
                    m.put("mandatory_count", list.stream().filter(g -> Boolean.TRUE.equals(g.getIsMandatory())).count());
                    m.put("employees_affected", list.stream()
                            .map(g -> scope.byId().get(g.getMatchingResultId()).getEmployeeId()).distinct().count());
                    top.add(m);
                });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runs_covered", scope.runsById().keySet().stream().sorted().toList());
        body.put("total_gaps", gaps.size());
        body.put("employees_with_gaps", employees.size());
        body.put("mandatory_gaps", mandatory);
        body.put("gaps_without_recommendation", noRecommendation);
        body.put("by_priority", byPriority);
        body.put("by_gap_type", byType);
        body.put("most_common_gap_skills", top);
        return body;
    }

    // ---------------- approved resources for one gap ----------------

    @Transactional(readOnly = true)
    public Map<String, Object> resourcesForGap(Integer skillGapId) {
        SkillGap g = skillGapRepository.findById(skillGapId)
                .orElseThrow(() -> new NoSuchElementException("Skill gap not found: " + skillGapId));
        Map<Integer, Skill> skills = candidatePool.skillsById();
        List<LearningResource> fit = LearningCatalogService.fitting(
                catalog.approvedBySkill(Set.of(g.getSkillId())).get(g.getSkillId()),
                g.getCurrentProficiency(), g.getRequiredProficiency());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skill_gap_id", g.getSkillGapId());
        body.put("skill_id", g.getSkillId());
        body.put("skill_name", skills.get(g.getSkillId()) != null ? skills.get(g.getSkillId()).getSkillName() : null);
        body.put("gap_type", g.getGapType());
        body.put("current_level", g.getCurrentProficiency());
        body.put("required_level", g.getRequiredProficiency());
        body.put("resources", fit.stream().map(r -> catalog.toMap(r, skills)).toList());
        if (fit.isEmpty()) {
            body.put("note", "No approved and active L&K resource fits this gap. "
                    + "Generating recommendations will use AI or the rule-based template, and L&K must review them.");
        }
        return body;
    }

    // ---------------- learning pathway ----------------

    /** Ek employee ka ek requirement ke run ke liye pathway. runId na ho to latest finished run. */
    @Transactional(readOnly = true)
    public Map<String, Object> pathwayForEmployee(Integer employeeId, Integer requirementId, Integer runId) {
        MatchingRun run = queryService.resolveRun(requirementId, runId);
        if (run == null) {
            throw new NoSuchElementException("Requirement " + requirementId + " has no finished matching run yet");
        }
        MatchingResult mr = matchingResultRepository.findByMatchingRunIdOrderByRankPositionAsc(run.getMatchingRunId())
                .stream().filter(r -> employeeId.equals(r.getEmployeeId())).findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "Employee " + employeeId + " is not in the results of run " + run.getMatchingRunId()));
        return pathway(mr, run);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pathwayForResult(Integer matchingResultId) {
        MatchingResult mr = matchingResultRepository.findById(matchingResultId)
                .orElseThrow(() -> new NoSuchElementException("Matching result not found: " + matchingResultId));
        MatchingRun run = matchingRunRepository.findById(mr.getMatchingRunId())
                .orElseThrow(() -> new NoSuchElementException("Matching run not found: " + mr.getMatchingRunId()));
        return pathway(mr, run);
    }

    private Map<String, Object> pathway(MatchingResult mr, MatchingRun run) {
        ProjectRequirement req = queryService.findRequirement(run.getRequirementId());
        Candidate c = candidatePool.loadByIds(Set.of(mr.getEmployeeId())).get(mr.getEmployeeId());
        Map<Integer, Skill> skills = candidatePool.skillsById();
        Map<Integer, SkillGap> gapsById = skillGapRepository.findByMatchingResultId(mr.getMatchingResultId()).stream()
                .collect(Collectors.toMap(SkillGap::getSkillGapId, Function.identity()));
        List<LearningRecommendation> recs = learningRecommendationRepository.findByMatchingResultId(mr.getMatchingResultId())
                .stream()
                .sorted(Comparator.comparingInt((LearningRecommendation r) -> GapPriorityRules.rank(r.getPriority()))
                        .thenComparing(r -> r.getSequenceOrder() == null ? 0 : r.getSequenceOrder())
                        .thenComparing(LearningRecommendation::getLearningRecommendationId))
                .toList();

        List<Map<String, Object>> steps = new ArrayList<>();
        BigDecimal hours = BigDecimal.ZERO;
        int weeks = 0;
        int estimated = 0;
        int unestimated = 0;
        int rejected = 0;
        int awaitingReview = 0;
        int aiWritten = 0;
        Map<String, Integer> byStatus = new TreeMap<>();
        Map<String, Integer> bySource = new TreeMap<>();
        int n = 0;
        for (LearningRecommendation lr : recs) {
            Map<String, Object> step = RequirementQueryService.learningMaps(List.of(lr), skills).get(0);
            SkillGap g = lr.getSkillGapId() == null ? null : gapsById.get(lr.getSkillGapId());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("step", ++n);
            out.putAll(step);
            out.put("gap_type", g != null ? g.getGapType() : null);
            out.put("gap_size", g != null ? g.getGapSize() : null);
            out.put("mandatory", g != null ? g.getIsMandatory() : null);
            steps.add(out);

            byStatus.merge(String.valueOf(lr.getReviewStatus()), 1, Integer::sum);
            bySource.merge(String.valueOf(lr.getRecommendationSource()), 1, Integer::sum);
            if ("PENDING".equals(lr.getReviewStatus())) {
                awaitingReview++;
            }
            if (MatchingPersistenceService.SOURCE_AI.equals(lr.getRecommendationSource())) {
                aiWritten++;
            }
            if ("REJECTED".equals(lr.getReviewStatus())) {
                rejected++; // reject hui step pathway ke total me nahi
                continue;
            }
            if (lr.getEstimatedHours() != null) {
                hours = hours.add(lr.getEstimatedHours());
                estimated++;
                if (lr.getEstimatedWeeks() != null) {
                    weeks += lr.getEstimatedWeeks();
                }
            } else {
                unestimated++;
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("matching_result_id", mr.getMatchingResultId());
        body.put("matching_run_id", mr.getMatchingRunId());
        body.put("requirement_id", req.getRequirementId());
        body.put("requirement_role", req.getRoleName());
        body.put("employee_id", mr.getEmployeeId());
        body.put("employee_code", c != null ? c.employeeCode() : null);
        body.put("resource_name", c != null ? c.name() : null);
        body.put("match_category", mr.getMatchCategory());
        body.put("steps", steps);

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("steps", steps.size());
        totals.put("steps_counted", estimated + unestimated);
        totals.put("rejected_steps", rejected);
        totals.put("estimated_steps", estimated);
        totals.put("unestimated_steps", unestimated);
        totals.put("total_estimated_hours", hours);
        totals.put("total_estimated_weeks", weeks); // steps ek ke baad ek karne par
        totals.put("awaiting_lk_review", awaitingReview);
        totals.put("ai_generated_steps", aiWritten);
        totals.put("by_review_status", byStatus);
        totals.put("by_source", bySource);
        body.put("totals", totals);

        List<String> notes = new ArrayList<>();
        if (unestimated > 0) {
            notes.add("Hours and weeks are known only for steps from the L&K repository; " + unestimated
                    + " step(s) have no estimate yet, so the totals cover the other steps only.");
        }
        if (aiWritten > 0) {
            notes.add(aiWritten + " step(s) were written by AI and must be reviewed by L&K against approved learning resources.");
        }
        if (!notes.isEmpty()) {
            body.put("notes", notes);
        }
        return body;
    }

    // ---------------- helpers ----------------

    private void validate(GapFilter f) {
        if (f.priority() != null && !PRIORITIES.contains(f.priority().trim().toUpperCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "priority must be one of CRITICAL, HIGH, MEDIUM, LOW");
        }
        if (f.gapType() != null && !GAP_TYPES.contains(f.gapType().trim().toUpperCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "gapType must be MISSING or BELOW_REQUIRED");
        }
        if (f.limit() != null && (f.limit() < 1 || f.limit() > MAX_LIMIT)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private static int limit(GapFilter f) {
        return f.limit() == null ? DEFAULT_LIMIT : f.limit();
    }

    /**
     * Kaun se runs: requirementId (+ optional runId) -> wo run (default latest finished);
     * sirf runId -> wo run; kuch nahi -> har requirement ka latest finished run.
     */
    private Scope scope(GapFilter f) {
        List<MatchingRun> runs = new ArrayList<>();
        if (f.requirementId() != null) {
            MatchingRun run = queryService.resolveRun(f.requirementId(), f.runId());
            if (run != null) {
                runs.add(run);
            }
        } else if (f.runId() != null) {
            runs.add(matchingRunRepository.findById(f.runId())
                    .orElseThrow(() -> new NoSuchElementException("Matching run not found: " + f.runId())));
        } else {
            List<Integer> ids = matchingRunRepository.latestFinishedRunIds(RequirementQueryService.FINISHED);
            if (!ids.isEmpty()) {
                runs.addAll(matchingRunRepository.findAllById(ids));
            }
        }
        if (runs.isEmpty()) {
            return new Scope(List.of(), Map.of(), Map.of(), List.of());
        }
        List<MatchingResult> results = matchingResultRepository.findByMatchingRunIdIn(
                runs.stream().map(MatchingRun::getMatchingRunId).toList());
        if (f.employeeId() != null) {
            results = results.stream().filter(r -> f.employeeId().equals(r.getEmployeeId())).toList();
        }
        if (results.isEmpty()) {
            return new Scope(List.of(), Map.of(), Map.of(), List.of());
        }
        Map<Integer, MatchingResult> byId = results.stream()
                .collect(Collectors.toMap(MatchingResult::getMatchingResultId, Function.identity()));
        Map<Integer, MatchingRun> runsById = runs.stream()
                .collect(Collectors.toMap(MatchingRun::getMatchingRunId, Function.identity()));
        List<SkillGap> gaps = skillGapRepository.findByMatchingResultIdIn(byId.keySet());
        return new Scope(results, byId, runsById, gaps);
    }

    private Map<Integer, List<LearningRecommendation>> recsByGap(Scope scope) {
        if (scope.byId().isEmpty()) {
            return Map.of();
        }
        return learningRecommendationRepository.findByMatchingResultIdIn(scope.byId().keySet()).stream()
                .filter(r -> r.getSkillGapId() != null)
                .collect(Collectors.groupingBy(LearningRecommendation::getSkillGapId));
    }

    private List<SkillGap> filtered(Scope scope, GapFilter f, Map<Integer, List<LearningRecommendation>> recsByGap) {
        String priority = f.priority() == null ? null : f.priority().trim().toUpperCase();
        String type = f.gapType() == null ? null : f.gapType().trim().toUpperCase();
        return scope.gaps().stream()
                .filter(g -> priority == null || priority.equals(g.getPriority()))
                .filter(g -> type == null || type.equals(g.getGapType()))
                .filter(g -> f.mandatory() == null || f.mandatory().equals(g.getIsMandatory()))
                .filter(g -> f.skillId() == null || f.skillId().equals(g.getSkillId()))
                .filter(g -> f.hasRecommendation() == null
                        || f.hasRecommendation() == recsByGap.containsKey(g.getSkillGapId()))
                .toList();
    }
}

