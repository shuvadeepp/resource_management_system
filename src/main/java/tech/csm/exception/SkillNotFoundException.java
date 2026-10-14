package tech.csm.exception;

import java.util.List;

public class SkillNotFoundException extends RuntimeException {

    private final List<String> missingSkills;

    public SkillNotFoundException(List<String> missingSkills) {
        super("Skills not found in skill master: " + missingSkills);
        this.missingSkills = missingSkills;
    }

    public List<String> getMissingSkills() { return missingSkills; }
}