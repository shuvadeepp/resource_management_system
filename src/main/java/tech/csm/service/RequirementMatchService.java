package tech.csm.service;

import java.util.HashMap;
import java.util.Map;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

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

@Service
public class RequirementMatchService {

    private static final Logger log = LoggerFactory.getLogger(RequirementMatchService.class);

    private final RequirementService requirementService;
    private final EmployeesRepository employeesRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final LlmClient llmClient;

    public RequirementMatchService(RequirementService requirementService,
                                   EmployeesRepository employeesRepository,
                                   EmployeeSkillRepository employeeSkillRepository,
                                   LlmClient llmClient) {
        this.requirementService = requirementService;
        this.employeesRepository = employeesRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.llmClient = llmClient;
    }

    public RequirementResponse createAndMatch(RequirementRequest request) {

        // 1. DB mein save
        ProjectRequirement saved = requirementService.save(request);

        // 2. DB se employees + skills
        List<EmployeeProfile> employees = buildEmployeeProfiles();

        // 3. LLM call. Fail hua to bhi requirement saved rehti hai
        LlmPayload payload = new LlmPayload(saved.getRequirementId(), request, employees);
        JsonNode llmResult;
        String message;
        try {
            llmResult = llmClient.analyze(payload);
            message = "Requirement saved and sent for matching";
        } catch (Exception ex) {
            log.error("LLM call failed for requirement {}", saved.getRequirementId(), ex);
            llmResult = null;
            message = "Requirement saved, but the LLM step failed";
        }
        enrichWithEmployeeDetails(llmResult, employees);
        
        return new RequirementResponse("success", message, saved.getRequirementId(),
                saved.getStatus(), employees.size(), llmResult);
    }
    
    private void enrichWithEmployeeDetails(JsonNode result, List<EmployeeProfile> employees) {
	    if (result == null || !result.path("matches").isArray()
	            || !(result.path("matches") instanceof ArrayNode matches)) {
	        return;
	    }
	
	    Map<String, EmployeeProfile> byCode = new HashMap<>();
	    for (EmployeeProfile e : employees) {
	        if (e.getEmployeeCode() != null) {
	            byCode.put(e.getEmployeeCode(), e);
	        }
	    }
	
	    // keys we want after the identity block, in this order
	    List<String> orderedKeys = List.of(
	            "match_score", "category", "matching_skills", "below_required_proficiency",
	            "missing_skills", "transferable_skills", "explanation", "learning_suggestions");
	
	    for (int i = 0; i < matches.size(); i++) {
	        JsonNode old = matches.get(i);
	        EmployeeProfile e = byCode.get(old.path("employee_code").textValue());
	
	        ObjectNode fresh = matches.objectNode();
	
	        // 1. identity first
	        fresh.put("employee_code", old.path("employee_code").textValue());
	        if (e != null) {
	            fresh.put("resource_name", e.getEmployeeName());
	            fresh.put("employee_id", e.getEmployeeId());
	            fresh.put("total_experience_years", e.getTotalExperienceYears());
	        }
	
	        // 2. Gemini's fields in a fixed order
	        for (String key : orderedKeys) {
	            if (old.has(key)) {
	                fresh.set(key, old.get(key));
	            }
	        }
	
	        // 3. anything else Gemini returned
	        for (String key : old.propertyNames()) {
	            if (!fresh.has(key)) {
	                fresh.set(key, old.get(key));
	            }
	        }
	
	        matches.set(i, fresh);
	    }
    }

    private List<EmployeeProfile> buildEmployeeProfiles() {
        List<EmployeeProfile> result = new ArrayList<>();

        for (Employees emp : employeesRepository.findAll()) {

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
}