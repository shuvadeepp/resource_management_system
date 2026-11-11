package tech.csm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tech.csm.model.RequiredSkill;
import tech.csm.model.Skill;
import tech.csm.model.SkillGap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class Stage3LogicTest {

    // ---------- evidence: validation status kabhi neeche nahi jaata ----------

    @Test
    void assessmentEvidenceGivesAssessmentVerified() {
        assertEquals("ASSESSMENT_VERIFIED", EvidenceService.upgradedValidation("SELF_REPORTED", "ASSESSMENT"));
        assertEquals("ASSESSMENT_VERIFIED", EvidenceService.upgradedValidation("RA_VALIDATED", "ASSESSMENT"));
    }

    @Test
    void otherEvidenceGivesAtLeastRaValidated() {
        assertEquals("RA_VALIDATED", EvidenceService.upgradedValidation("SELF_REPORTED", "CERTIFICATION"));
        assertEquals("RA_VALIDATED", EvidenceService.upgradedValidation(null, "PROJECT_EXPERIENCE"));
    }

    @Test
    void validationNeverGoesDown() {
        assertEquals("ASSESSMENT_VERIFIED", EvidenceService.upgradedValidation("ASSESSMENT_VERIFIED", "CERTIFICATION"));
        assertEquals("RA_VALIDATED", EvidenceService.upgradedValidation("RA_VALIDATED", "OTHER"));
    }

    // ---------- compare: gap ka badlaav ----------

    private static SkillGap gap(int skillId, String type, Integer current, int required, String priority) {
        SkillGap g = new SkillGap();
        g.setSkillId(skillId);
        g.setGapType(type);
        g.setCurrentProficiency(current);
        g.setRequiredProficiency(required);
        g.setIsMandatory(true);
        g.setPriority(priority);
        return g;
    }

    private static String status(List<Map<String, Object>> changes, int skillId) {
        return (String) changes.stream().filter(m -> m.get("skill_id").equals(skillId)).findFirst().orElseThrow().get("status");
    }

    @Test
    void diffClassifiesClosedNewImprovedWorsenedUnchanged() {
        List<SkillGap> before = List.of(
                gap(1, "MISSING", null, 3, "CRITICAL"),          // closed
                gap(2, "BELOW_REQUIRED", 2, 4, "HIGH"),          // improved to 3
                gap(3, "BELOW_REQUIRED", 3, 4, "HIGH"),          // worsened to 2
                gap(4, "BELOW_REQUIRED", 2, 4, "HIGH"));         // unchanged
        List<SkillGap> after = List.of(
                gap(2, "BELOW_REQUIRED", 3, 4, "HIGH"),
                gap(3, "BELOW_REQUIRED", 2, 4, "HIGH"),
                gap(4, "BELOW_REQUIRED", 2, 4, "HIGH"),
                gap(5, "MISSING", null, 2, "CRITICAL"));         // new
        List<Map<String, Object>> changes = RunCompareService.diffGaps(before, after, Map.of());
        assertEquals("CLOSED", status(changes, 1));
        assertEquals("IMPROVED", status(changes, 2));
        assertEquals("WORSENED", status(changes, 3));
        assertEquals("UNCHANGED", status(changes, 4));
        assertEquals("NEW", status(changes, 5));
    }

    @Test
    void missingToBelowRequiredCountsAsImproved() {
        List<Map<String, Object>> changes = RunCompareService.diffGaps(
                List.of(gap(1, "MISSING", null, 3, "CRITICAL")),
                List.of(gap(1, "BELOW_REQUIRED", 1, 3, "HIGH")), Map.of());
        assertEquals("IMPROVED", status(changes, 1));
        assertNull(changes.get(0).get("old_level"));
        assertEquals("MISSING", changes.get(0).get("old_gap_type"));
    }

    // ---------- backfill: JSON snapshot -> gaps ----------

    private static Skill skill(int id, String name) {
        Skill s = new Skill();
        s.setSkillId(id);
        s.setSkillName(name);
        return s;
    }

    private static RequiredSkill required(int skillId, boolean mandatory, int min) {
        RequiredSkill r = new RequiredSkill();
        r.setSkillId(skillId);
        r.setIsMandatory(mandatory);
        r.setMinimumProficiency(min);
        return r;
    }

    @Test
    void backfillReadsMissingAndBelowFromSnapshot() throws Exception {
        JsonNode json = new ObjectMapper().readTree(
                "{\"missing_skills\":[\"Docker\",\"Rust\"],\"below_required_proficiency\":[{\"skill\":\"MySQL\",\"has\":2,\"needs\":3}]}");
        Map<String, Skill> skills = Map.of("docker", skill(10, "Docker"), "mysql", skill(11, "MySQL"));
        Map<Integer, RequiredSkill> req = Map.of(10, required(10, true, 2), 11, required(11, false, 4));

        GapBackfillService.Parsed p = GapBackfillService.parse(json, skills, req);

        assertEquals(1, p.unknownSkill());               // Rust is not in the skill master
        assertEquals(2, p.gaps().size());
        var docker = p.gaps().get(0);
        assertEquals(10, docker.skill().skillId());
        assertEquals(2, docker.skill().minProficiency()); // from required_skills
        assertEquals(true, docker.skill().mandatory());
        assertNull(docker.currentLevel());
        var mysql = p.gaps().get(1);
        assertEquals(3, mysql.skill().minProficiency());  // "needs" from the snapshot wins over today's 4
        assertEquals(2, mysql.currentLevel());
    }

    @Test
    void missingSkillThatIsNoLongerInTheRequirementIsSkipped() throws Exception {
        JsonNode json = new ObjectMapper().readTree("{\"missing_skills\":[\"Docker\"],\"below_required_proficiency\":[]}");
        GapBackfillService.Parsed p = GapBackfillService.parse(json, Map.of("docker", skill(10, "Docker")), Map.of());
        assertEquals(0, p.gaps().size());
        assertEquals(1, p.notInRequirement());
    }
}
