package tech.csm.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Matching pipeline ke plain data types. Inme koi JPA entity nahi hai, isliye
 * ye bina transaction ke background thread ko bhi diye ja sakte hain.
 */
public final class MatchingData {

    private MatchingData() {
    }

    public static final String AI_USED = "USED";
    public static final String AI_FAILED = "FAILED";
    public static final String AI_DISABLED = "DISABLED";

    public record CandidateSkill(Integer skillId, String skillName, String category,
                                 Integer proficiency, Integer experienceMonths, String validationStatus) {
    }

    public record Candidate(Integer employeeId, String employeeCode, String name,
                            String designation, String department, Double experienceYears,
                            List<CandidateSkill> skills,
                            String availabilityStatus, String availableFrom, BigDecimal capacityPercentage) {
    }

    /** Requirement ki ek skill. skillId null = skill master me nahi mili (sirf preview me ho sakta hai). */
    public record ReqSkill(Integer skillId, String skillName, String category, boolean mandatory, int minProficiency) {
    }

    public record Spec(List<ReqSkill> skills, Integer minExperienceMonths) {
        public boolean hasSkills() {
            return skills != null && !skills.isEmpty();
        }
    }

    /** Ek skill gap. currentLevel null = skill hai hi nahi. */
    public record Gap(ReqSkill skill, Integer currentLevel) {
        public boolean missing() {
            return currentLevel == null;
        }
    }

    public record Transferable(String skill, String relatedTo) {
    }

    /** Java rule score (FRD weights) aur us score ke peeche ka breakdown. */
    public record Scored(Candidate candidate, double ruleScore, List<String> matchingSkills,
                         List<Gap> gaps, List<Transferable> transferable, boolean mandatoryMissing) {
    }

    public record LearningItem(ReqSkill skill, Integer currentLevel, String action,
                               String resource, String resourceType, String gapDescription) {
    }

    public record FinalMatch(Scored scored, BigDecimal score, String category, int rank,
                             String explanation, List<LearningItem> learning, String scoredBy) {
    }

    /** aiStatus: USED / FAILED / DISABLED. aiError sirf FAILED par. */
    public record Outcome(List<FinalMatch> matches, int poolSize, String aiStatus, String aiError) {
    }
}
