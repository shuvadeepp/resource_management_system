package tech.csm.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import tech.csm.model.Employees;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.ProjectRequirement;
import tech.csm.model.RequiredSkill;
import tech.csm.model.Skill;
import tech.csm.repository.EmployeesRepository;
import tech.csm.repository.LearningRecommendationRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
import tech.csm.repository.ProjectRequirementRepository;
import tech.csm.repository.RequiredSkillRepository;
import tech.csm.repository.SkillRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Saved requirements aur unke persisted matches padhne ke liye - naye GET
 * endpoints (RequirementController) ke peeche ka logic. Koi existing
 * endpoint/response yahan se touch nahi hota.
 */
@Service
public class RequirementQueryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProjectRequirementRepository requirementRepository;
    private final RequiredSkillRepository requiredSkillRepository;
    private final SkillRepository skillRepository;
    private final MatchingRunRepository matchingRunRepository;
    private final MatchingResultRepository matchingResultRepository;
    private final LearningRecommendationRepository learningRecommendationRepository;
    private final EmployeesRepository employeesRepository;

    public RequirementQueryService(ProjectRequirementRepository requirementRepository,
                                   RequiredSkillRepository requiredSkillRepository,
                                   SkillRepository skillRepository,
                                   MatchingRunRepository matchingRunRepository,
                                   MatchingResultRepository matchingResultRepository,
                                   LearningRecommendationRepository learningRecommendationRepository,
                                   EmployeesRepository employeesRepository) {
        this.requirementRepository = requirementRepository;
        this.requiredSkillRepository = requiredSkillRepository;
        this.skillRepository = skillRepository;
        this.matchingRunRepository = matchingRunRepository;
        this.matchingResultRepository = matchingResultRepository;
        this.learningRecommendationRepository = learningRecommendationRepository;
        this.employeesRepository = employeesRepository;
    }

    public List<Map<String, Object>> listRequirements() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProjectRequirement r : requirementRepository.findAll()) {
            out.add(toRequirementMap(r));
        }
        return out;
    }

    public Map<String, Object> getRequirement(Integer id) {
        ProjectRequirement r = requirementRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Requirement not found: " + id));
        return toRequirementMap(r);
    }

    private Map<String, Object> toRequirementMap(ProjectRequirement r) {
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
        for (RequiredSkill rs : requiredSkillRepository.findByRequirementId(r.getRequirementId())) {
            Map<String, Object> s = new LinkedHashMap<>();
            Skill skill = skillRepository.findById(rs.getSkillId()).orElse(null);
            s.put("skill_id", rs.getSkillId());
            s.put("skill_name", skill != null ? skill.getSkillName() : null);
            s.put("is_mandatory", rs.getIsMandatory());
            s.put("minimum_proficiency", rs.getMinimumProficiency());
            skills.add(s);
        }
        m.put("skills", skills);
        return m;
    }

    /** Requirement ki sabse recent matching_run ke saare persisted matches. */
    public List<Map<String, Object>> getMatches(Integer requirementId) {
        List<MatchingRun> runs = matchingRunRepository.findByRequirementIdOrderByMatchingRunIdDesc(requirementId);
        if (runs.isEmpty()) {
            return List.of();
        }
        MatchingRun latest = runs.get(0);

        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchingResult mr : matchingResultRepository.findByMatchingRunIdOrderByRankPositionAsc(latest.getMatchingRunId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("matching_result_id", mr.getMatchingResultId());
            m.put("matching_run_id", mr.getMatchingRunId());

            Employees emp = employeesRepository.findById(mr.getEmployeeId()).orElse(null);
            m.put("employee_id", mr.getEmployeeId());
            m.put("employee_code", emp != null ? emp.getEmployeeCode() : null);
            m.put("resource_name", emp != null ? emp.getName() : null);

            m.put("overall_score", mr.getOverallScore());
            m.put("match_category", mr.getMatchCategory());
            m.put("strengths", parseJsonSafely(mr.getStrengths()));
            m.put("skill_gaps", parseJsonSafely(mr.getSkillGaps()));
            m.put("explanation", mr.getExplanation());
            m.put("rank_position", mr.getRankPosition());
            m.put("recommendation_status", mr.getRecommendationStatus());

            List<Map<String, Object>> learning = new ArrayList<>();
            learningRecommendationRepository.findByMatchingResultId(mr.getMatchingResultId()).forEach(lr -> {
                Map<String, Object> l = new LinkedHashMap<>();
                Skill skill = skillRepository.findById(lr.getSkillId()).orElse(null);
                l.put("skill_name", skill != null ? skill.getSkillName() : null);
                l.put("target_proficiency", lr.getTargetProficiency());
                l.put("learning_action", lr.getLearningAction());
                learning.add(l);
            });
            m.put("learning_suggestions", learning);

            out.add(m);
        }
        return out;
    }

    private JsonNode parseJsonSafely(String raw) {
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
