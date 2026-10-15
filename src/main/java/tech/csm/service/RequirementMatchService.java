package tech.csm.service;

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

        return new RequirementResponse("success", message, saved.getRequirementId(),
                saved.getStatus(), employees.size(), llmResult);
    }

    private List<EmployeeProfile> buildEmployeeProfiles() {
        List<EmployeeProfile> result = new ArrayList<>();

        for (Employees emp : employeesRepository.findAll()) {

            List<EmployeeSkillProfile> skills = new ArrayList<>();
            for (EmployeeSkill es : employeeSkillRepository
                    .findByEmployee_EmpIdAndIsActive(emp.getEmployeeId(), 1)) {
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