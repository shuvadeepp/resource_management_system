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

    @Transactional
    public ProjectRequirement save(RequirementRequest request) {

        // 1. Har skill name ko skills table se match karo, nahi mila to reject
        Map<RequiredSkillRequest, Skill> resolved = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        for (RequiredSkillRequest rs : request.getRequiredSkills()) {
            skillRepository.findBySkillNameIgnoreCase(rs.getSkillName().trim())
                    .ifPresentOrElse(
                            skill -> resolved.put(rs, skill),
                            () -> missing.add(rs.getSkillName()));
        }
        if (!missing.isEmpty()) {
            throw new SkillNotFoundException(missing);
        }

        // 2. Requirement save (existing project_requirements table ke columns)
        ProjectRequirement pr = new ProjectRequirement();
        pr.setProjectId(request.getProjectId() != null ? request.getProjectId() : defaultProjectId);
        pr.setRequirementCode(
                request.getProjectCode() != null && !request.getProjectCode().isBlank()
                        ? request.getProjectCode()
                        : "REQ-" + System.currentTimeMillis());
        pr.setRoleName(request.getProjectName());
        pr.setRoleDescription(request.getDescription());
        pr.setProjectDurationMonths(request.getDurationMonths());
        pr.setLocation(request.getLocation());

        if (request.getWorkMode() != null) {
            pr.setWorkMode(request.getWorkMode().trim().toUpperCase());
        }
        if (request.getPriority() != null) {
            pr.setRequirementPriority(request.getPriority().trim().toUpperCase());
        }
        if (request.getHeadcount() != null) {
            pr.setRequiredHeadcount(request.getHeadcount());
        }
        if (request.getMinimumExperienceYears() != null) {
            pr.setMinimumExperienceMonths(
                    request.getMinimumExperienceYears().multiply(BigDecimal.valueOf(12)).intValue());
        }

        pr.setJoiningDate(request.getJoiningDate());
        pr.setRequirementOwner(defaultRequirementOwner);
        pr = requirementRepository.save(pr);

        // 3. Required skills save (har skill ke liye naya object, PK set nahi karni)
        List<RequiredSkill> toSave = new ArrayList<>();
        for (Map.Entry<RequiredSkillRequest, Skill> e : resolved.entrySet()) {
            RequiredSkill rs = new RequiredSkill();
            rs.setRequirementId(pr.getRequirementId());
            rs.setSkillId(e.getValue().getSkillId());
            rs.setIsMandatory(e.getKey().getMandatory());
            rs.setMinimumProficiency(e.getKey().getMinimumProficiency());
            toSave.add(rs);
        }
        requiredSkillRepository.saveAll(toSave);

        return pr;
    }
}