package tech.csm.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tech.csm.dto.EmployeeProfile;
import tech.csm.dto.EmployeeSkillProfile;
import tech.csm.dto.LlmPayload;
import tech.csm.dto.RequirementRequest;
import tech.csm.dto.RequirementResponse;
import tech.csm.llm.LlmClient;
import tech.csm.model.EmployeeSkill;
import tech.csm.model.Employees;
import tech.csm.model.ProjectRequirement;
import tech.csm.model.Skill;
import tech.csm.repository.EmployeeSkillRepository;
import tech.csm.repository.EmployeesRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class RequirementMatchService {

    private static final Logger log = LoggerFactory.getLogger(RequirementMatchService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RequirementService requirementService;
    private final EmployeesRepository employeesRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final LlmClient llmClient;
    private final MatchingPersistenceService matchingPersistenceService;

    public RequirementMatchService(RequirementService requirementService,
                                   EmployeesRepository employeesRepository,
                                   EmployeeSkillRepository employeeSkillRepository,
                                   LlmClient llmClient,
                                   MatchingPersistenceService matchingPersistenceService) {
        this.requirementService = requirementService;
        this.employeesRepository = employeesRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.llmClient = llmClient;
        this.matchingPersistenceService = matchingPersistenceService;
    }

    public RequirementResponse createAndMatch(RequirementRequest request) {

        // 1. DB me save sirf tab, jab project_name aur joining_date dono diye hon
        ProjectRequirement saved = null;
        if (hasText(request.projectName()) && request.joiningDate() != null) {
            saved = requirementService.save(request);
        }
        Integer requirementId = saved != null ? saved.getRequirementId() : null;
        String requirementStatus = saved != null ? saved.getStatus() : null;

        // 2. Filter (designation, department, experience), phir profiles
        List<Employees> filtered = filterEmployees(request);
        List<EmployeeProfile> employees = buildEmployeeProfiles(filtered);

        boolean hasSkills = request.requiredSkills() != null && !request.requiredSkills().isEmpty();

        JsonNode llmResult;
        String message;

        if (filtered.isEmpty()) {
            ObjectNode empty = MAPPER.createObjectNode();
            empty.put("total_qualified_matches", 0);
            empty.putArray("matches");
            empty.put("note", "No employees match the given filters.");
            llmResult = empty;
            message = "No employees found for the given filters";

        } else if (!hasSkills) {
            // Skills nahi diye: Gemini ki zaroorat nahi, filter hue employees seedha do
            llmResult = buildPlainList(filtered, employees);
            message = "Employees filtered (no skills given, so no scoring)";

        } else {
            LlmPayload payload = new LlmPayload(requirementId, request, employees);
            try {
                llmResult = llmClient.analyze(payload);
                message = "Requirement processed and matched";
            } catch (Exception ex) {
                log.error("LLM call failed", ex);

                Throwable root = ex;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                ObjectNode err = MAPPER.createObjectNode();
                err.put("error", ex.getClass().getSimpleName());
                err.put("detail", String.valueOf(ex.getMessage()));
                err.put("root_cause", root.getClass().getSimpleName() + ": " + root.getMessage());
                llmResult = err;
                message = "Search done, but the LLM step failed";
            }
            enrichWithEmployeeDetails(llmResult, employees);

            // Side-effect only: response shape ko yahan kuch nahi badalta.
            // requirementId null ho (requirement DB me save nahi hui) to persist() khud skip kar deta hai.
            matchingPersistenceService.persist(requirementId, llmResult, employees.size());
        }

        return new RequirementResponse("success", message, requirementId,
                requirementStatus, employees.size(), llmResult);
    }

    // ---------------- filters ----------------

    private List<Employees> filterEmployees(RequirementRequest r) {
        List<Employees> out = new ArrayList<>();

        for (Employees e : employeesRepository.findAll()) {

            if (hasText(r.designation())) {
                String name = e.getDesignation() != null ? e.getDesignation().getDesignationName() : null;
                if (!contains(name, r.designation())) {
                    continue;
                }
            }

            if (hasText(r.department())) {
                String name = e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null;
                if (!contains(name, r.department())) {
                    continue;
                }
            }

            if (r.minimumExperienceYears() != null) {
                Double exp = e.getTotalExperienceYears();
                if (exp == null || exp < r.minimumExperienceYears().doubleValue()) {
                    continue;
                }
            }

            out.add(e);
        }
        return out;
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search.trim().toLowerCase());
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    // ---------------- profiles ----------------

    private List<EmployeeProfile> buildEmployeeProfiles(List<Employees> list) {
        List<EmployeeProfile> result = new ArrayList<>();

        for (Employees emp : list) {
            List<EmployeeSkillProfile> skills = new ArrayList<>();
            for (EmployeeSkill es : employeeSkillRepository
                    .findByEmployee_employeeIdAndIsActive(emp.getEmployeeId(), 1)) {
                skills.add(toSkillProfile(es));
            }

            result.add(new EmployeeProfile(     
                    emp.getEmployeeId(),
                    emp.getEmployeeCode(),
                    emp.getName(),
                    emp.getDesignation() != null ? emp.getDesignation().getDesignationId() : null,
                    emp.getDepartment() != null ? emp.getDepartment().getDepartmentId() : null,
                    emp.getLocation() != null ? emp.getLocation().getLocationId() : null,
                    emp.getTotalExperienceYears(),
                    skills));
        }
        return result;
    }

    private EmployeeSkillProfile toSkillProfile(EmployeeSkill es) {
        Skill skill = es.getSkill();
        return new EmployeeSkillProfile(
                skill != null ? skill.getSkillId() : null,
                skill != null ? skill.getSkillName() : null,
                es.getProficiencyLevel(),
                es.getExperienceMonths(),
                es.getValidationStatus());
    }

    // ---------------- result without Gemini ----------------

    private JsonNode buildPlainList(List<Employees> filtered, List<EmployeeProfile> profiles) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("total_qualified_matches", filtered.size());
        ArrayNode matches = root.putArray("matches");

        for (int i = 0; i < filtered.size(); i++) {
            Employees emp = filtered.get(i);
            EmployeeProfile p = profiles.get(i);

            ObjectNode m = matches.addObject();
            m.put("employee_code", p.getEmployeeCode());
            m.put("resource_name", p.getEmployeeName());
            m.put("employee_id", p.getEmployeeId());
            m.put("designation", emp.getDesignation() != null ? emp.getDesignation().getDesignationName() : null);
            m.put("department", emp.getDepartment() != null ? emp.getDepartment().getDepartmentName() : null);
            m.put("total_experience_years", p.getTotalExperienceYears());

            ArrayNode skills = m.putArray("skills");
            if (p.getSkills() != null) {
                for (EmployeeSkillProfile s : p.getSkills()) {
                    ObjectNode sn = skills.addObject();
                    sn.put("name", s.getSkillName());
                    sn.put("proficiency", s.getProficiencyLevel());
                    sn.put("experience_months", s.getExperienceMonths());
                }
            }
        }
        root.put("note", "Filter result only. Provide required_skills to get match scores.");
        return root;
    }

    // ---------------- Gemini result: name etc. in clean order ----------------

    private void enrichWithEmployeeDetails(JsonNode result, List<EmployeeProfile> employees) {
        if (result == null || !(result.path("matches") instanceof ArrayNode matches)) {
            return;
        }

        Map<String, EmployeeProfile> byCode = new HashMap<>();
        for (EmployeeProfile e : employees) {
            if (e.getEmployeeCode() != null) {
                byCode.put(e.getEmployeeCode(), e);
            }
        }

        List<String> orderedKeys = List.of(
                "match_score", "category", "matching_skills", "below_required_proficiency",
                "missing_skills", "transferable_skills", "explanation", "learning_suggestions");

        for (int i = 0; i < matches.size(); i++) {
            JsonNode old = matches.get(i);
            EmployeeProfile e = byCode.get(old.path("employee_code").textValue());

            ObjectNode fresh = matches.objectNode();

            fresh.put("employee_code", old.path("employee_code").textValue());
            if (e != null) {
                fresh.put("resource_name", e.getEmployeeName());
                fresh.put("employee_id", e.getEmployeeId());
                fresh.put("total_experience_years", e.getTotalExperienceYears());
            }

            for (String key : orderedKeys) {
                if (old.has(key)) {
                    fresh.set(key, old.get(key));
                }
            }
            for (String key : old.propertyNames()) {
                if (!fresh.has(key)) {
                    fresh.set(key, old.get(key));
                }
            }
            matches.set(i, fresh);
        }
    }
}