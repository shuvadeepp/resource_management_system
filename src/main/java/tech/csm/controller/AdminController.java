package tech.csm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tech.csm.service.GapBackfillService;

/** Sirf ADMIN (SecurityConfig: /api/admin/**). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final GapBackfillService backfillService;

    public AdminController(GapBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    /**
     * Purane runs ke liye skill_gaps rows (run ke time ke JSON snapshot se). Default dryRun=true - pehle
     * ginti dekho, phir dryRun=false se likho. Scope: ?runId= ya ?requirementId=, kuch na do to saare finished runs.
     */
    @PostMapping("/skill-gaps/backfill")
    public ResponseEntity<?> backfill(@RequestParam(required = false) Integer requirementId,
                                      @RequestParam(required = false) Integer runId,
                                      @RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(backfillService.backfill(requirementId, runId, dryRun));
    }
}
