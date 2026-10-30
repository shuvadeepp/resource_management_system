package tech.csm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.dto.RequiredSkillRequest;
import tech.csm.dto.RequirementRequest;
import tech.csm.exception.ApiException;
import tech.csm.exception.RequirementValidationException;
import tech.csm.exception.SkillNotFoundException;
import tech.csm.model.Project;
import tech.csm.model.ProjectRequirement;
import tech.csm.model.RequiredSkill;
import tech.csm.model.Skill;
import tech.csm.repository.ProjectRepository;
import tech.csm.repository.ProjectRequirementRepository;
import tech.csm.repository.RequiredSkillRepository;
import tech.csm.repository.SkillRepository;

@Service
public class RequirementService {

    // project_requirements.requirement_code VARCHAR(60)
    private static final int MAX_CODE_LENGTH = 60;

    private final ProjectRequirementRepository requirementRepository;
    private final RequiredSkillRepository requiredSkillRepository;
    private final SkillRepository skillRepository;
    private final ProjectRepository projectRepository;

    @Value("${app.default-project-id}")
    private Integer defaultProjectId;

    @Value("${app.default-requirement-owner}")
    private Integer defaultRequirementOwner;

    public RequirementService(ProjectRequirementRepository requirementRepository,
                              RequiredSkillRepository requiredSkillRepository,
                              SkillRepository skillRepository,
                              ProjectRepository projectRepository) {
        this.requirementRepository = requirementRepository;
        this.requiredSkillRepository = requiredSkillRepository;
        this.skillRepository = skillRepository;
        this.projectRepository = projectRepository;
    }

    // Purane callers ke liye (bina IP)
    @Transactional
    public ProjectRequirement save(RequirementRequest request) {
        return save(request, null);
    }

    // Caller ensures project_name and joining_date are present (RequirementValidator)
    @Transactional
    public ProjectRequirement save(RequirementRequest request, String ipAddress) {

        // 0. project_id diya hai to projects me hona chahiye (FK fk_req_project); nahi diya to default
        Project project = null;
        if (request.projectId() != null) {
            project = projectRepository.findById(request.projectId())
                    .orElseThrow(() -> new RequirementValidationException(
                            "project_id " + request.projectId() + " does not exist in projects",
                            Map.of("project_id", "does not exist in projects")));
        }

        List<RequiredSkillRequest> requestedSkills =
                request.requiredSkills() != null ? request.requiredSkills() : List.of();

        // 1. Skill names ko skills table se match karo, nahi mila to reject.
        //    Skill master ek hi query me (har skill ke liye alag query nahi).
        Map<String, Skill> master = new java.util.HashMap<>();
        for (Skill s : skillRepository.findAll()) {
            if (s.getSkillName() != null) {
                master.put(s.getSkillName().trim().toLowerCase(), s);
            }
        }
        Map<RequiredSkillRequest, Skill> resolved = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        for (RequiredSkillRequest rs : requestedSkills) {
            Skill skill = master.get(rs.skillName().trim().toLowerCase());
            if (skill != null) {
                resolved.put(rs, skill);
            } else {
                missing.add(rs.skillName());
            }
        }
        if (!missing.isEmpty()) {
            throw new SkillNotFoundException(missing);
        }

        // 2. Requirement save
        ProjectRequirement pr = new ProjectRequirement();
        pr.setProjectId(project != null ? project.getProjectId() : defaultProjectId);
        pr.setRequirementCode(requirementCode(request, project));
        pr.setRoleName(request.projectName());
        pr.setRoleDescription(request.description());
        pr.setProjectDurationMonths(request.durationMonths());
        pr.setLocation(request.location());

        if (request.workMode() != null) {
            pr.setWorkMode(request.workMode().trim().toUpperCase());
        }
        if (request.priority() != null) {
            pr.setRequirementPriority(request.priority().trim().toUpperCase());
        }
        if (request.headcount() != null) {
            pr.setRequiredHeadcount(request.headcount());
        }
        if (request.minimumExperienceYears() != null) {
            pr.setMinimumExperienceMonths(
                    request.minimumExperienceYears().multiply(BigDecimal.valueOf(12)).intValue());
        }

        pr.setJoiningDate(request.joiningDate());
        pr.setRequirementOwner(defaultRequirementOwner);
        pr.setIpAddress(ipAddress);
        pr = requirementRepository.save(pr);

        // 3. Required skills save
        List<RequiredSkill> toSave = new ArrayList<>();
        for (Map.Entry<RequiredSkillRequest, Skill> e : resolved.entrySet()) {
            RequiredSkill rs = new RequiredSkill();
            rs.setRequirementId(pr.getRequirementId());
            rs.setSkillId(e.getValue().getSkillId());
            rs.setIsMandatory(e.getKey().mandatory() != null ? e.getKey().mandatory() : false);
            rs.setMinimumProficiency(
                    e.getKey().minimumProficiency() != null ? e.getKey().minimumProficiency() : 1);
            toSave.add(rs);
        }
        requiredSkillRepository.saveAll(toSave);

        return pr;
    }

    /**
     * requirement_code UNIQUE hai:
     *  - request ka project_code -> wahi; pehle se ho to 409 (user ka diya code chupke se nahi badalte)
     *  - warna project ka project_code (PRJ-002); pehle se ho to PRJ-002-2, PRJ-002-3, ...
     *  - project hi nahi -> REQ-<timestamp> (purana behaviour)
     */
    private String requirementCode(RequirementRequest request, Project project) {
        if (request.projectCode() != null && !request.projectCode().isBlank()) {
            String code = request.projectCode().trim();
            if (requirementRepository.existsByRequirementCode(code)) {
                throw new ApiException(HttpStatus.CONFLICT, "requirement code " + code
                        + " already exists; send a different project_code or leave it out");
            }
            return code;
        }
        if (project != null && project.getProjectCode() != null && !project.getProjectCode().isBlank()) {
            String base = project.getProjectCode().trim();
            String code = base;
            for (int n = 2; requirementRepository.existsByRequirementCode(code); n++) {
                String suffix = "-" + n;
                int max = MAX_CODE_LENGTH - suffix.length();
                code = (base.length() > max ? base.substring(0, max) : base) + suffix;
            }
            return code;
        }
        return "REQ-" + System.currentTimeMillis();
    }
}