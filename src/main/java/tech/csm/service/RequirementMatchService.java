package tech.csm.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import tech.csm.repository.SkillRepository;

@Service
public class RequirementMatchService {

    private static final Logger log = LoggerFactory.getLogger(RequirementMatchService.class);

    private final RequirementService requirementService;
    private final EmployeesRepository employeesRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final SkillRepository skillRepository;
    private final LlmClient llmClient;

    public RequirementMatchService(RequirementService requirementService,
                                   EmployeesRepository employeesRepository,
                                   EmployeeSkillRepository employeeSkillRepository,
                                   SkillRepository skillRepository,
                                   LlmClient llmClient) {
        this.requirementService = requirementService;
        this.employeesRepository = employeesRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.skillRepository = skillRepository;
        this.llmClient = llmClient;
    }

    public RequirementResponse createAndMatch(RequirementRequest request) {

        // 1. DB mein save (alag transaction, LLM call se pehle commit ho jata hai)
        ProjectRequirement saved = requirementService.save(request);

        // 2. DB se employees + skills
        List<EmployeeProfile> employees = buildEmployeeProfiles();

        // 3. LLM call. Fail hua to bhi requirement saved rehti hai
        LlmPayload payload = new LlmPayload(saved.getRequirementId(), request, employees);
        String llmResult;
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
        Map<Integer, String> skillNames = new HashMap<>();
        for (Skill s : skillRepository.findAll()) {
            skillNames.put(s.getSkillId(), s.getSkillName());
        }

        List<EmployeeProfile> result = new ArrayList<>();

        for (Employees emp : employeesRepository.findAll()) {

            List<EmployeeSkillProfile> skills = new ArrayList<>();
            for (EmployeeSkill es : employeeSkillRepository
                    .findByEmployeeIdAndIsActiveTrue(emp.getEmployeeId())) {
                skills.add(toSkillProfile(es, skillNames));
            }

            result.add(new EmployeeProfile(
            		emp.getEmpId(),
                    emp.getEmployeeId(), 
                    emp.getName(),                    
                    emp.getDesignation().getDesignationId(),
                    emp.getDepartment().getDepartmentId(),
                    emp.getLocation().getLocationId(),
                    emp.getTotalExperienceYears(),
                    skills));
        }
        return result;
    }

    private EmployeeSkillProfile toSkillProfile(EmployeeSkill es, Map<Integer, String> skillNames) {
        return new EmployeeSkillProfile(
                es.getSkillId(),
                skillNames.get(es.getSkillId()),
                Objects.toString(es.getProficiencyLevel(), null),
                es.getExperienceMonths(),
                Objects.toString(es.getValidationStatus(), null));
    }
}