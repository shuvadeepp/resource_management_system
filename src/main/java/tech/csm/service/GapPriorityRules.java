package tech.csm.service;

import java.util.List;

/**
 * FR-GAP-03: gap ki priority Java rules se tay hoti hai (AI se nahi).
 *   mandatory + missing        -> CRITICAL
 *   mandatory + below required -> HIGH
 *   preferred + missing        -> MEDIUM
 *   preferred + below required -> LOW
 * Phir requirement_priority HIGH ya CRITICAL ho to ek level upar (CRITICAL se upar nahi).
 */
public final class GapPriorityRules {

    private static final List<String> LADDER = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public record Result(String priority, String reason) {
    }

    private GapPriorityRules() {
    }

    public static Result priorityFor(boolean missing, boolean mandatory, String requirementPriority) {
        int level;
        String reason;
        if (mandatory) {
            level = missing ? 3 : 2;
            reason = missing ? "Mandatory skill is missing" : "Mandatory skill is below the required level";
        } else {
            level = missing ? 1 : 0;
            reason = missing ? "Preferred skill is missing" : "Preferred skill is below the required level";
        }
        String rp = requirementPriority == null ? "" : requirementPriority.trim().toUpperCase();
        if (("HIGH".equals(rp) || "CRITICAL".equals(rp)) && level < LADDER.size() - 1) {
            level++;
            reason += "; raised one level because the requirement priority is " + rp;
        }
        return new Result(LADDER.get(level), reason);
    }

    /** Sorting ke liye: 0 = sabse urgent. Anjaani value sabse neeche. */
    public static int rank(String priority) {
        int i = priority == null ? -1 : LADDER.indexOf(priority.trim().toUpperCase());
        return i < 0 ? LADDER.size() : LADDER.size() - 1 - i;
    }
}

