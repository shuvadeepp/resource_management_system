package tech.csm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tech.csm.service.SkillGapReportService;
import tech.csm.service.SkillGapReportService.GapFilter;

/**
 * Module 5 reports: gap list + summary (FR-GAP-01..03), gap ke liye approved resources (FR-GAP-05),
 * learning pathway (FR-GAP-06).
 *
 * Gap list / summary: requirementId (latest finished run) ya runId do; kuch na do to har requirement ka
 * latest finished run (workforce-wide).
 */
@RestController
@RequestMapping("/api")
public class SkillGapReportController {

    private final SkillGapReportService reports;

    public SkillGapReportController(SkillGapReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/skill-gaps")
    public ResponseEntity<?> list(@RequestParam(required = false) Integer requirementId,
                                  @RequestParam(required = false) Integer runId,
                                  @RequestParam(required = false) Integer employeeId,
                                  @RequestParam(required = false) String priority,
                                  @RequestParam(required = false) String gapType,
                                  @RequestParam(required = false) Boolean mandatory,
                                  @RequestParam(required = false) Integer skillId,
                                  @RequestParam(required = false) Boolean hasRecommendation,
                                  @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(reports.list(new GapFilter(requirementId, runId, employeeId, priority, gapType,
                mandatory, skillId, hasRecommendation, limit)));
    }

    @GetMapping("/skill-gaps/summary")
    public ResponseEntity<?> summary(@RequestParam(required = false) Integer requirementId,
                                     @RequestParam(required = false) Integer runId,
                                     @RequestParam(required = false) Integer employeeId,
                                     @RequestParam(required = false) String priority,
                                     @RequestParam(required = false) String gapType,
                                     @RequestParam(required = false) Boolean mandatory,
                                     @RequestParam(required = false) Integer skillId,
                                     @RequestParam(required = false) Boolean hasRecommendation,
                                     @RequestParam(defaultValue = "10") int topN) {
        return ResponseEntity.ok(reports.summary(new GapFilter(requirementId, runId, employeeId, priority, gapType,
                mandatory, skillId, hasRecommendation, null), topN));
    }

    // Approved + active L&K resources jo is gap ke level ko fit hoti hain
    @GetMapping("/skill-gaps/{id}/resources")
    public ResponseEntity<?> resources(@PathVariable Integer id) {
        return ResponseEntity.ok(reports.resourcesForGap(id));
    }

    @GetMapping("/matching-results/{id}/learning-pathway")
    public ResponseEntity<?> pathwayForResult(@PathVariable Integer id) {
        return ResponseEntity.ok(reports.pathwayForResult(id));
    }

    // Employee ka pathway ek requirement ke liye (runId na ho to latest finished run)
    @GetMapping("/employees/{id}/learning-pathway")
    public ResponseEntity<?> pathwayForEmployee(@PathVariable Integer id,
                                                @RequestParam Integer requirementId,
                                                @RequestParam(required = false) Integer runId) {
        return ResponseEntity.ok(reports.pathwayForEmployee(id, requirementId, runId));
    }
}

