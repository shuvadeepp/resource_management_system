package tech.csm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import tech.csm.model.LearningResource;

class LearningCatalogServiceTest {

    private static LearningResource res(int id, int from, int to, String hours, boolean approved, boolean active) {
        LearningResource r = new LearningResource();
        r.setLearningResourceId(id);
        r.setSkillId(1);
        r.setFromProficiency(from);
        r.setToProficiency(to);
        r.setEstimatedHours(hours == null ? null : new BigDecimal(hours));
        r.setIsApproved(approved);
        r.setIsActive(active);
        return r;
    }

    @Test
    void picksResourceThatCoversTheLevelRange() {
        LearningResource fits = res(1, 1, 4, "10.0", true, true);
        LearningResource tooLow = res(2, 1, 2, "5.0", true, true);       // does not reach required 4
        LearningResource tooAdvanced = res(3, 3, 5, "3.0", true, true);   // learner at 2 is below from 3
        assertEquals(1, LearningCatalogService.pick(List.of(tooLow, tooAdvanced, fits), 2, 4).getLearningResourceId());
    }

    @Test
    void ignoresUnapprovedAndInactiveResources() {
        List<LearningResource> all = List.of(res(1, 1, 5, "2.0", false, true), res(2, 1, 5, "2.0", true, false));
        assertNull(LearningCatalogService.pick(all, 1, 3));
    }

    @Test
    void shortestHoursWinsAndMissingHoursComesLast() {
        List<LearningResource> all = List.of(res(1, 1, 5, null, true, true), res(2, 1, 5, "20.0", true, true),
                res(3, 1, 5, "8.0", true, true));
        List<LearningResource> fit = LearningCatalogService.fitting(all, null, 3);
        assertEquals(List.of(3, 2, 1), fit.stream().map(LearningResource::getLearningResourceId).toList());
    }

    @Test
    void missingSkillCountsAsBeginnerLevelOne() {
        assertEquals(1, LearningCatalogService.pick(List.of(res(1, 1, 3, "4.0", true, true)), null, 3).getLearningResourceId());
        assertTrue(LearningCatalogService.fitting(List.of(res(2, 2, 4, "4.0", true, true)), null, 3).isEmpty());
    }

    @Test
    void weeksRoundUpAndNeedHours() {
        assertEquals(2, LearningCatalogService.weeks(new BigDecimal("10.0"), 5));
        assertEquals(3, LearningCatalogService.weeks(new BigDecimal("10.5"), 5));
        assertEquals(1, LearningCatalogService.weeks(new BigDecimal("0.5"), 5));
        assertNull(LearningCatalogService.weeks(null, 5));
        assertEquals(10, LearningCatalogService.weeks(new BigDecimal("10.0"), 0)); // bad config -> 1 hour/week
    }
}
