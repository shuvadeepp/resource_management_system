package tech.csm.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tech.csm.dto.RequiredSkillRequest;
import tech.csm.dto.RequirementRequest;
import tech.csm.exception.RequirementValidationException;

/**
 * POST /api/requirements rules:
 *  - project_name, joining_date ya required_skills me se KOI bhi aaye -> ye "save + match" request hai,
 *    aur tab chaaron mandatory: project_name, required_skills (>= 1, har ek ka skill_name),
 *    minimum_experience_years (0 allowed), joining_date.
 *  - warna (no body / {} / sirf filters) -> list-only, kuch save nahi.
 * Khaali string aur khaali array = diya hi nahi.
 */
@Component
public class RequirementValidator {

    public static boolean isMatchingRequest(RequirementRequest r) {
        return hasText(r.projectName()) || r.joiningDate() != null
                || (r.requiredSkills() != null && !r.requiredSkills().isEmpty());
    }

    /** Saari problems ek saath jama karke ek hi 400 me. */
    public void validate(RequirementRequest r) {
        Map<String, String> fields = new LinkedHashMap<>();

        if (!hasText(r.projectName())) {
            fields.put("project_name", "required");
        }

        List<RequiredSkillRequest> skills = r.requiredSkills();
        if (skills == null || skills.isEmpty()) {
            fields.put("required_skills", "required");
        } else {
            for (int i = 0; i < skills.size(); i++) {
                RequiredSkillRequest s = skills.get(i);
                if (s == null || !hasText(s.skillName())) {
                    fields.put("required_skills[" + i + "].skill_name", "required");
                }
                if (s != null && s.minimumProficiency() != null
                        && (s.minimumProficiency() < 1 || s.minimumProficiency() > 5)) {
                    fields.put("required_skills[" + i + "].minimum_proficiency", "must be 1 to 5");
                }
            }
        }

        if (r.minimumExperienceYears() == null) {
            fields.put("minimum_experience_years", "required");
        } else if (r.minimumExperienceYears().signum() < 0) {
            fields.put("minimum_experience_years", "must be 0 or more");
        }

        if (r.joiningDate() == null) {
            fields.put("joining_date", "required");
        }

        if (!fields.isEmpty()) {
            throw new RequirementValidationException(
                    "Missing or invalid: " + String.join(", ", fields.keySet()), fields);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
