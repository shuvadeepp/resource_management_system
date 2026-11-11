package tech.csm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import tech.csm.model.SkillGap;
import tech.csm.service.MatchingData.Gap;
import tech.csm.service.MatchingData.ReqSkill;

class GapPriorityRulesTest {

    @Test
    void basePriorityFollowsMandatoryAndMissing() {
        assertEquals("CRITICAL", GapPriorityRules.priorityFor(true, true, "MEDIUM").priority());
        assertEquals("HIGH", GapPriorityRules.priorityFor(false, true, "MEDIUM").priority());
        assertEquals("MEDIUM", GapPriorityRules.priorityFor(true, false, "MEDIUM").priority());
        assertEquals("LOW", GapPriorityRules.priorityFor(false, false, "LOW").priority());
    }

    @Test
    void highOrCriticalRequirementRaisesOneLevel() {
        assertEquals("MEDIUM", GapPriorityRules.priorityFor(false, false, "HIGH").priority());
        assertEquals("HIGH", GapPriorityRules.priorityFor(true, false, "critical").priority());
        assertEquals("CRITICAL", GapPriorityRules.priorityFor(false, true, "HIGH").priority());
        assertTrue(GapPriorityRules.priorityFor(false, true, "HIGH").reason().contains("raised one level"));
    }

    @Test
    void criticalNeverGoesHigherAndNullPriorityIsIgnored() {
        assertEquals("CRITICAL", GapPriorityRules.priorityFor(true, true, "CRITICAL").priority());
        assertEquals("CRITICAL", GapPriorityRules.priorityFor(true, true, null).priority());
    }

    @Test
    void rankPutsCriticalFirst() {
        assertTrue(GapPriorityRules.rank("CRITICAL") < GapPriorityRules.rank("HIGH"));
        assertTrue(GapPriorityRules.rank("LOW") < GapPriorityRules.rank(null));
    }

    private static Gap gap(boolean mandatory, int min, Integer current) {
        return new Gap(new ReqSkill(7, "Docker", null, mandatory, min), current);
    }

    @Test
    void missingSkillStoresNullCurrentAndFullGapSize() {
        SkillGap row = MatchingPersistenceService.toSkillGap(1, gap(true, 3, null), "MEDIUM");
        assertEquals(SkillGap.MISSING, row.getGapType());
        assertNull(row.getCurrentProficiency());
        assertEquals(3, row.getGapSize());
        assertEquals("CRITICAL", row.getPriority());
    }

    @Test
    void belowRequiredKeepsCurrentLevel() {
        SkillGap row = MatchingPersistenceService.toSkillGap(1, gap(false, 4, 2), "MEDIUM");
        assertEquals(SkillGap.BELOW_REQUIRED, row.getGapType());
        assertEquals(2, row.getCurrentProficiency());
        assertEquals(2, row.getGapSize());
        assertEquals("LOW", row.getPriority());
    }

    @Test
    void unratedSkillIsStoredAsMissingSoCheckConstraintsHold() {
        // scorer skill rating null ko level 0 banata hai; DB CHECK current 1..5 chahta hai
        SkillGap row = MatchingPersistenceService.toSkillGap(1, gap(true, 3, 0), "MEDIUM");
        assertEquals(SkillGap.MISSING, row.getGapType());
        assertNull(row.getCurrentProficiency());
        assertTrue(row.getPriorityReason().contains("no rating"));
    }

    @Test
    void duplicateSkillGivesOneRowAndSkillOutsideMasterIsSkipped() {
        Gap notInMaster = new Gap(new ReqSkill(null, "Unknown", null, true, 3), null);
        List<SkillGap> rows = MatchingPersistenceService.toSkillGaps(1,
                List.of(gap(true, 3, null), gap(false, 4, 1), notInMaster), "MEDIUM");
        assertEquals(1, rows.size());
        assertNotNull(rows.get(0).getSkillId());
    }
}
