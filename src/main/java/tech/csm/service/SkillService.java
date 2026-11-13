package tech.csm.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.dto.SkillRequest;
import tech.csm.exception.ApiException;
import tech.csm.model.Skill;
import tech.csm.repository.SkillRepository;

/** Skill master ka create / read / update / delete. */
@Service
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Integer id) {
        return toMap(find(id));
    }

    @Transactional
    public Map<String, Object> create(SkillRequest request) {
        String name = request.skillName().trim();
        skillRepository.findBySkillNameIgnoreCase(name).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "Skill already exists: " + existing.getSkillName());
        });

        Skill skill = new Skill();
        apply(skill, name, request.skillCategory());
        return toMap(skillRepository.save(skill));
    }

    @Transactional
    public Map<String, Object> update(Integer id, SkillRequest request) {
        Skill skill = find(id);
        String name = request.skillName().trim();
        // Dusre skill ka naam hi taken ho to conflict; apna hi naam wapas bhejna theek hai
        skillRepository.findBySkillNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getSkillId().equals(id)) {
                throw new ApiException(HttpStatus.CONFLICT, "Skill already exists: " + existing.getSkillName());
            }
        });

        apply(skill, name, request.skillCategory());
        return toMap(skillRepository.save(skill));
    }

    public void delete(Integer id) {
        Skill skill = find(id);
        try {
            skillRepository.delete(skill);
            skillRepository.flush(); // FK violation yahin pakadne ke liye
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Skill '" + skill.getSkillName() + "' is in use (employee skills, requirements or learning "
                            + "resources) and cannot be deleted");
        }
    }

    private Skill find(Integer id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Skill not found: " + id));
    }

    private static void apply(Skill skill, String name, String category) {
        skill.setSkillName(name);
        skill.setSkillCategory(category == null || category.isBlank() ? null : category.trim());
    }

    // Skill entity me id aur category @JsonIgnore hain (/api/skills ka shape na badle), isliye yahan explicit map
    private static Map<String, Object> toMap(Skill s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("skill_id", s.getSkillId());
        row.put("skill_name", s.getSkillName());
        row.put("skill_category", s.getSkillCategory());
        return row;
    }
}
