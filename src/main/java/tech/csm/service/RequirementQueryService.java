package tech.csm.service;

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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.model.AllocationDecision;
import tech.csm.model.LearningRecommendation;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.ProjectRequirement;
import tech.csm.model.RequiredSkill;
import tech.csm.model.Skill;
import tech.csm.model.User;
import tech.csm.repository.AllocationDecisionRepository;
import tech.csm.repository.LearningRecommendationRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
import tech.csm.repository.ProjectRequirementRepository;
import tech.csm.repository.RequiredSkillRepository;
import tech.csm.repository.UserRepo;
import tech.csm.service.MatchingData.Candidate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Saved requirements aur unke stored matches padhne ka logic. Har method fixed
 * number of queries chalata hai (rows ki ginti se independent) aur ek
 * read-only transaction me.
 */
@Service
public class RequirementQueryService {

    /** "Latest run" = sabse naya run jo khatam ho chuka (chal rahe run ke khaali results nahi dikhte). */
    public static final List<String> FINISHED = List.of(
            MatchingPersistenceService.COMPLETED, MatchingPersistenceService.COMPLETED_WITH_ERRORS);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProjectRequirementRepository requirementRepository;
    private final RequiredSkillRepository requiredSkillRepository;
    private final MatchingRunRepository matchingRunRepository;
    private final MatchingResultRepository matchingResultRepository;
    private final LearningRecommendationRepository learningRecommendationRepository;
    private final AllocationDecisionRepository allocationDecisionRepository;
    private final UserRepo userRepo;
    private final CandidatePoolService candidatePool;

    public RequirementQueryService(ProjectRequirementRepository requirementRepository,
                                   RequiredSkillRepository requiredSkillRepository,
                                   MatchingRunRepository matchingRunRepository,
                                   MatchingResultRepository matchingResultRepository,
                                   LearningRecommendationRepository learningRecommendationRepository,
                                   AllocationDecisionRepository allocationDecisionRepository,
                                   UserRepo userRepo,
                                   CandidatePoolService candidatePool) {
        this.requirementRepository = requirementRepository;
        this.requiredSkillRepository = requiredSkillRepository;
        this.matchingRunRepository = matchingRunRepository;
        this.matchingResultRepository = matchingResultRepository;
        this.learningRecommendationRepository = learningRecommendationRepository;
        this.allocationDecisionRepository = allocationDecisionRepository;
        this.userRepo = userRepo;
        this.candidatePool = candidatePool;
    }

    // ---------------- requirements ----------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRequirements() {
        List<ProjectRequirement> requirements = requirementRepository.findAll();
        List<Integer> ids = requirements.stream().map(ProjectRequirement::getRequirementId).toList();
        Map<Integer, List<RequiredSkill>> skillsByRequirement = ids.isEmpty() ? Map.of()
                : requiredSkillRepository.findByRequirementIdIn(ids).stream()
                        .collect(Collectors.groupingBy(RequiredSkill::getRequirementId));
        Map<Integer, Skill> skillsById = candidatePool.skillsById();

        List<Map<String, Object>> out = new ArrayList<>();
        for (ProjectRequirement r : requirements) {
            out.add(toRequirementMap(r, skillsByRequirement.getOrDefault(r.getRequirementId(), List.of()), skillsById));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRequirement(Integer id) {
        ProjectRequirement r = findRequirement(id);
        return toRequirementMap(r, requiredSkillRepository.findByRequirementId(id), candidatePool.skillsById());
    }

    public ProjectRequirement findRequirement(Integer id) {
        return requirementRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Requirement not found: " + id));
    }

    private Map<String, Object> toRequirementMap(ProjectRequirement r, List<RequiredSkill> rows, Map<Integer, Skill> skillsById) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requirement_id", r.getRequirementId());
        m.put("project_id", r.getProjectId());
        m.put("requirement_code", r.getRequirementCode());
        m.put("role_name", r.getRoleName());
        m.put("role_description", r.getRoleDescription());
        m.put("required_headcount", r.getRequiredHeadcount());
        m.put("minimum_proficiency_level", r.getMinimumProficiencyLevel());
        m.put("minimum_experience_months", r.getMinimumExperienceMonths());
        m.put("joining_date", r.getJoiningDate());
        m.put("project_duration_months", r.getProjectDurationMonths());
        m.put("location", r.getLocation());
        m.put("work_mode", r.getWorkMode());
        m.put("requirement_priority", r.getRequirementPriority());
        m.put("requirement_status", r.getStatus());
        m.put("created_at", r.getCreatedAt());

        List<Map<String, Object>> skills = new ArrayList<>();
        for (RequiredSkill rs : rows) {
            Map<String, Object> s = new LinkedHashMap<>();
            Skill skill = skillsById.get(rs.getSkillId());
            s.put("skill_id", rs.getSkillId());
            s.put("skill_name", skill != null ? skill.getSkillName() : null);
            s.put("is_mandatory", rs.getIsMandatory());
            s.put("minimum_proficiency", rs.getMinimumProficiency());
            skills.add(s);
        }
        m.put("skills", skills);
        return m;
    }

    // ---------------- matches ----------------

    /** runId diya ho to wo run (isi requirement ka hona chahiye), warna latest finished run. */
    public MatchingRun resolveRun(Integer requirementId, Integer runId) {
        if (runId != null) {
            MatchingRun run = matchingRunRepository.findById(runId)
                    .orElseThrow(() -> new NoSuchElementException("Matching run not found: " + runId));
            if (!run.getRequirementId().equals(requirementId)) {
                throw new NoSuchElementException("Run " + runId + " does not belong to requirement " + requirementId);
            }
            return run;
        }
        List<MatchingRun> runs = matchingRunRepository
                .findByRequirementIdAndRunStatusInOrderByMatchingRunIdDesc(requirementId, FINISHED);
        return runs.isEmpty() ? null : runs.get(0);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMatches(Integer requirementId, Integer runId) {
        MatchingRun run = resolveRun(requirementId, runId);
        if (run == null) {
            return List.of();
        }
        List<MatchingResult> results = matchingResultRepository.findByMatchingRunIdOrderByRankPositionAsc(run.getMatchingRunId());
        if (results.isEmpty()) {
            return List.of();
        }

        List<Integer> resultIds = results.stream().map(MatchingResult::getMatchingResultId).toList();
        Map<Integer, Candidate> candidates = candidatePool.loadByIds(
                results.stream().map(MatchingResult::getEmployeeId).collect(Collectors.toSet()));
        Map<Integer, Skill> skillsById = candidatePool.skillsById();
        Map<Integer, List<LearningRecommendation>> learning = learningRecommendationRepository
                .findByMatchingResultIdIn(resultIds).stream()
                .collect(Collectors.groupingBy(LearningRecommendation::getMatchingResultId));
        Map<Integer, AllocationDecision> latestDecision = latestDecisions(resultIds);
        Map<Integer, User> makers = users(latestDecision.values().stream()
                .map(AllocationDecision::getDecisionMaker).collect(Collectors.toSet()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchingResult mr : results) {
            Candidate c = candidates.get(mr.getEmployeeId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("matching_result_id", mr.getMatchingResultId());
            m.put("matching_run_id", mr.getMatchingRunId());
            m.put("employee_id", mr.getEmployeeId());
            m.put("employee_code", c != null ? c.employeeCode() : null);
            m.put("resource_name", c != null ? c.name() : null);
            m.put("designation", c != null ? c.designation() : null);
            m.put("department", c != null ? c.department() : null);
            m.put("total_experience_years", c != null ? c.experienceYears() : null);
            m.put("availability", c != null ? RequirementMatchService.availability(c) : null);
            m.put("overall_score", mr.getOverallScore());
            m.put("match_category", mr.getMatchCategory());
            m.put("rank_position", mr.getRankPosition());
            m.put("recommendation_status", mr.getRecommendationStatus());
            m.put("strengths", parseJson(mr.getStrengths()));
            m.put("skill_gaps", parseJson(mr.getSkillGaps()));
            m.put("explanation", mr.getExplanation());
            m.put("learning_suggestions", learningMaps(
                    learning.getOrDefault(mr.getMatchingResultId(), List.of()), skillsById));
            m.put("current_decision", decisionMap(latestDecision.get(mr.getMatchingResultId()), makers));
            out.add(m);
        }
        return out;
    }

    /** Har matching_result ka sabse naya decision (decision_date, phir id). Purane history me rehte hain. */
    public Map<Integer, AllocationDecision> latestDecisions(List<Integer> matchingResultIds) {
        Comparator<AllocationDecision> newest = Comparator
                .comparing(AllocationDecision::getDecisionDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(AllocationDecision::getAllocationDecisionId);
        Map<Integer, AllocationDecision> out = new HashMap<>();
        for (AllocationDecision d : allocationDecisionRepository.findByMatchingResultIdIn(matchingResultIds)) {
            AllocationDecision current = out.get(d.getMatchingResultId());
            if (current == null || newest.compare(d, current) > 0) {
                out.put(d.getMatchingResultId(), d);
            }
        }
        return out;
    }

    public Map<Integer, User> users(Set<Integer> ids) {
        ids.remove(null);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepo.findAllById(ids).stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
    }

    static Map<String, Object> decisionMap(AllocationDecision d, Map<Integer, User> makers) {
        if (d == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("allocation_decision_id", d.getAllocationDecisionId());
        m.put("decision", d.getDecision());
        m.put("decision_reason", d.getDecisionReason());
        m.put("decision_maker", d.getDecisionMaker());
        User u = makers.get(d.getDecisionMaker());
        m.put("decision_maker_name", u != null ? u.getFullName() : null);
        m.put("decision_date", d.getDecisionDate());
        m.put("allocation_id", d.getAllocationId());
        return m;
    }

    static List<Map<String, Object>> learningMaps(List<LearningRecommendation> recs, Map<Integer, Skill> skillsById) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearningRecommendation lr : recs) {
            Map<String, Object> l = new LinkedHashMap<>();
            Skill skill = skillsById.get(lr.getSkillId());
            l.put("learning_recommendation_id", lr.getLearningRecommendationId());
            l.put("skill_name", skill != null ? skill.getSkillName() : null);
            l.put("current_proficiency", lr.getCurrentProficiency());
            l.put("target_proficiency", lr.getTargetProficiency());
            l.put("learning_action", lr.getLearningAction());
            l.put("learning_resource", lr.getLearningResource());
            l.put("resource_type", lr.getResourceType());
            l.put("review_status", lr.getReviewStatus());
            l.put("review_comments", lr.getReviewComments());
            out.add(l);
        }
        return out;
    }

    static JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception ex) {
            return null;
        }
    }
}
