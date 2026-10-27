package tech.csm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.dto.RequiredSkillRequest;
import tech.csm.dto.RequirementRequest;
import tech.csm.exception.SkillNotFoundException;
import tech.csm.model.ProjectRequirement;
import tech.csm.model.RequiredSkill;
import tech.csm.model.Skill;
import tech.csm.repository.ProjectRequirementRepository;
import tech.csm.repository.RequiredSkillRepository;
import tech.csm.repository.SkillRepository;

@Service
public class RequirementService {

    private final ProjectRequirementRepository requirementRepository;
    private final RequiredSkillRepository requiredSkillRepository;
    private final SkillRepository skillRepository;

    @Value("${app.default-project-id}")
    private Integer defaultProjectId;

    @Value("${app.default-requirement-owner}")
    private Integer defaultRequirementOwner;

    public RequirementService(ProjectRequirementRepository requirementRepository,
                              RequiredSkillRepository requiredSkillRepository,
                              SkillRepository skillRepository) {
        this.requirementRepository = requirementRepository;
        this.requiredSkillRepository = requiredSkillRepository;
        this.skillRepository = skillRepository;
    }

    // Caller ensures project_name and joining_date are present
    @Transactional
    public ProjectRequirement save(RequirementRequest request) {

        List<RequiredSkillRequest> requestedSkills =
                request.requiredSkills() != null ? request.requiredSkills() : List.of();

        // 1. Skill names ko skills table se match karo, nahi mila to reject
        Map<RequiredSkillRequest, Skill> resolved = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        for (RequiredSkillRequest rs : requestedSkills) {
            skillRepository.findBySkillNameIgnoreCase(rs.skillName().trim())
                    .ifPresentOrElse(
                            skill -> resolved.put(rs, skill),
                            () -> missing.add(rs.skillName()));
        }
        if (!missing.isEmpty()) {
            throw new SkillNotFoundException(missing);
        }

        // 2. Requirement save
        ProjectRequirement pr = new ProjectRequirement();
        pr.setProjectId(request.projectId() != null ? request.projectId() : defaultProjectId);
        pr.setRequirementCode(
                request.projectCode() != null && !request.projectCode().isBlank()
                        ? request.projectCode()
                        : "REQ-" + System.currentTimeMillis());
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
}