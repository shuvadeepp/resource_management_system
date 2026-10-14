package tech.csm.service;
 

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RequirementService {

    private final ProjectRequirementRepository requirementRepository;
    private final RequiredSkillRepository requiredSkillRepository;
    private final SkillRepository skillRepository;

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

        for (RequiredSkillRequest rs : request.requiredSkills()) {
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
        pr.setProjectName(request.projectName());
        pr.setProjectCode(request.projectCode());
        pr.setDescription(request.description());
        pr.setBusinessDomain(request.businessDomain());
        pr.setLocationId(request.locationId());
        pr.setWorkMode(request.workMode());
        pr.setDurationMonths(request.durationMonths());
        pr.setHeadcount(request.headcount());
        pr.setPriority(request.priority());
        pr.setMinimumExperienceYears(request.minimumExperienceYears());
        pr.setJoiningDate(request.joiningDate());
        pr = requirementRepository.save(pr);

        // 3. Required skills save
        for (Map.Entry<RequiredSkillRequest, Skill> e : resolved.entrySet()) {
            RequiredSkill rs = new RequiredSkill();
            rs.setRequirementId(pr.getRequirementId());
            rs.setSkillId(e.getValue().getSkillId());
            rs.setIsMandatory(e.getKey().mandatory());
            rs.setMinimumProficiency(e.getKey().minimumProficiency());
            requiredSkillRepository.save(rs);
        }
        return pr;
    }
}