package tech.csm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import tech.csm.service.MatchingData.Candidate;
import tech.csm.service.MatchingData.CandidateSkill;
import tech.csm.service.MatchingData.Gap;
import tech.csm.service.MatchingData.LearningItem;
import tech.csm.service.MatchingData.ReqSkill;
import tech.csm.service.MatchingData.Scored;
import tech.csm.service.MatchingData.Spec;
import tech.csm.service.MatchingData.Transferable;

/**
 * Deterministic rule scoring (FRD weights):
 *   skill match 40 + proficiency 25 + experience 15 + availability 10 + other 10 = 100.
 * "other" = skill evidence quality (validation_status of the matched skills).
 * Mandatory skill missing -> score max 49. Gemini ke bina bhi har run yahi se score hota hai.
 */
@Component
public class MatchScorer {

    private static final Set<String> RESOURCE_TYPES = Set.of(
            "COURSE", "CERTIFICATION", "DOCUMENTATION", "MENTORING", "PRACTICAL_ASSIGNMENT", "OTHER");

    public Scored score(Spec spec, Candidate c) {
        Map<String, CandidateSkill> have = new HashMap<>();
        for (CandidateSkill cs : c.skills()) {
            if (cs.skillName() != null) {
                have.put(cs.skillName().toLowerCase(), cs);
            }
        }

        List<String> matching = new ArrayList<>();
        List<Gap> gaps = new ArrayList<>();
        List<Transferable> transferable = new ArrayList<>();
        boolean mandatoryMissing = false;

        double totalWeight = 0, matchedWeight = 0, proficiencySum = 0, evidenceSum = 0;
        int matchedCount = 0;

        for (ReqSkill r : spec.skills()) {
            double w = r.mandatory() ? 2 : 1;
            totalWeight += w;
            CandidateSkill cs = have.get(r.skillName().toLowerCase());
            if (cs != null) {
                int level = cs.proficiency() != null ? cs.proficiency() : 0;
                matchedWeight += w;
                matchedCount++;
                matching.add(r.skillName());
                proficiencySum += w * Math.min(1.0, level / (double) Math.max(1, r.minProficiency()));
                evidenceSum += evidenceWeight(cs.validationStatus());
                if (level < r.minProficiency()) {
                    gaps.add(new Gap(r, level));
                }
            } else {
                gaps.add(new Gap(r, null));
                if (r.mandatory()) {
                    mandatoryMissing = true;
                }
                CandidateSkill related = relatedSkill(c, r, have);
                if (related != null) {
                    transferable.add(new Transferable(related.skillName(), r.skillName()));
                }
            }
        }

        double skillPart = totalWeight == 0 ? 0 : 40 * matchedWeight / totalWeight;
        double proficiencyPart = totalWeight == 0 ? 0 : 25 * proficiencySum / totalWeight;
        double experiencePart = experiencePart(spec, c);
        double availabilityPart = availabilityPart(c);
        double otherPart = matchedCount == 0 ? 0 : 10 * evidenceSum / matchedCount;

        double total = skillPart + proficiencyPart + experiencePart + availabilityPart + otherPart;
        if (mandatoryMissing) {
            total = Math.min(total, 49);
        }
        total = Math.round(total * 100) / 100.0;
        return new Scored(c, total, matching, gaps, transferable, mandatoryMissing);
    }

    /** Pool me aane ke liye: kam se kam ek required skill ya ek related (same category) skill. */
    public boolean eligible(Scored s) {
        return !s.matchingSkills().isEmpty() || !s.transferable().isEmpty();
    }

    public static String category(BigDecimal score) {
        if (score == null) return "LOW";
        double v = score.doubleValue();
        if (v >= 80) return "EXCELLENT";
        if (v >= 65) return "GOOD";
        if (v >= 50) return "PARTIAL";
        return "LOW";
    }

    public static BigDecimal toScore(double v) {
        return BigDecimal.valueOf(Math.max(0, Math.min(100, v))).setScale(2, RoundingMode.HALF_UP);
    }

    /** Short template explanation, 2 sentences max. */
    public String explanation(Scored s, Spec spec) {
        List<String> missing = new ArrayList<>();
        List<String> below = new ArrayList<>();
        for (Gap g : s.gaps()) {
            (g.missing() ? missing : below).add(g.skill().skillName());
        }
        StringBuilder first = new StringBuilder();
        if (s.matchingSkills().isEmpty()) {
            first.append("Has none of the ").append(spec.skills().size()).append(" required skills");
        } else {
            first.append("Has ").append(s.matchingSkills().size()).append(" of ").append(spec.skills().size())
                    .append(" required skills (").append(String.join(", ", s.matchingSkills())).append(")");
        }
        if (!missing.isEmpty()) {
            first.append("; missing ").append(String.join(", ", missing));
        }
        if (!below.isEmpty()) {
            first.append("; below the required level in ").append(String.join(", ", below));
        }
        first.append(".");

        Candidate c = s.candidate();
        String exp = c.experienceYears() != null ? trimNumber(c.experienceYears()) + " years of experience" : "Experience not available";
        String avail = c.availabilityStatus() != null ? c.availabilityStatus().toLowerCase().replace('_', ' ') : "not available";
        return first + " " + exp + ", availability: " + avail + ".";
    }

    /** Har gap ke liye template learning action (Gemini na ho ya us skill ke liye kuch na de). */
    public LearningItem templateLearning(Gap g) {
        ReqSkill r = g.skill();
        String action;
        String type;
        if (g.missing()) {
            action = "Learn " + r.skillName() + " up to level " + r.minProficiency()
                    + " through a course followed by a small practical task.";
            type = "COURSE";
        } else {
            action = "Raise " + r.skillName() + " from level " + g.currentLevel() + " to " + r.minProficiency()
                    + " with a mentored practical assignment.";
            type = "PRACTICAL_ASSIGNMENT";
        }
        return new LearningItem(r, g.currentLevel(), action, null, type, gapDescription(g));
    }

    public static String gapDescription(Gap g) {
        ReqSkill r = g.skill();
        if (g.missing()) {
            return (r.mandatory() ? "Missing mandatory skill " : "Missing skill ") + r.skillName()
                    + " (needs level " + r.minProficiency() + ")";
        }
        return "Has level " + g.currentLevel() + " in " + r.skillName() + ", needs level " + r.minProficiency();
    }

    /** Gemini ka resource_type text -> DB enum; anjaana -> OTHER. */
    public static String resourceType(String raw) {
        if (raw == null) return "OTHER";
        String v = raw.trim().toUpperCase().replace(' ', '_');
        return RESOURCE_TYPES.contains(v) ? v : "OTHER";
    }

    // ---------------- parts ----------------

    private CandidateSkill relatedSkill(Candidate c, ReqSkill r, Map<String, CandidateSkill> have) {
        if (r.category() == null) {
            return null;
        }
        for (CandidateSkill cs : c.skills()) {
            if (cs.category() != null && cs.category().equalsIgnoreCase(r.category())
                    && cs.skillName() != null && !cs.skillName().equalsIgnoreCase(r.skillName())) {
                return cs;
            }
        }
        return null;
    }

    private double experiencePart(Spec spec, Candidate c) {
        Integer need = spec.minExperienceMonths();
        if (need == null || need <= 0) {
            return 15;
        }
        double haveMonths = c.experienceYears() != null ? c.experienceYears() * 12 : 0;
        return 15 * Math.min(1.0, haveMonths / need);
    }

    private double availabilityPart(Candidate c) {
        String status = c.availabilityStatus();
        if (status == null) return 5;
        return switch (status.toUpperCase()) {
            case "BENCH" -> 10;
            case "PARTIALLY_AVAILABLE" -> c.capacityPercentage() != null
                    ? 10 * Math.min(100, c.capacityPercentage().doubleValue()) / 100 : 5;
            default -> 0; // ALLOCATED, UNAVAILABLE
        };
    }

    private double evidenceWeight(String validationStatus) {
        if (validationStatus == null) return 0.5;
        return switch (validationStatus.toUpperCase()) {
            case "ASSESSMENT_VERIFIED" -> 1.0;
            case "RA_VALIDATED" -> 0.75;
            default -> 0.5; // SELF_REPORTED
        };
    }

    private static String trimNumber(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
