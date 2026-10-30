package tech.csm.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tech.csm.dto.AllocationRequest;
import tech.csm.dto.DecisionRequest;
import tech.csm.dto.RecommendationRequest;
import tech.csm.service.DecisionService;

import java.util.Map;

/**
 * Shortlist -> Approve (SELECTED) / Hold (ON_HOLD) / Reject (REJECTED) -> Confirm allocation.
 */
@RestController
@RequestMapping("/api")
public class DecisionController {

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    // "Shortlisted" - recommendation_status = RECOMMENDED. Decision row nahi banti.
    @PostMapping("/matching-results/{id}/shortlist")
    public ResponseEntity<?> shortlist(@PathVariable Integer id) {
        return ResponseEntity.ok(decisionService.shortlist(id));
    }

    // Shortlist undo / koi bhi recommendation_status set karna.
    @PutMapping("/matching-results/{id}/recommendation")
    public ResponseEntity<?> setRecommendation(@PathVariable Integer id,
                                               @Valid @RequestBody RecommendationRequest request) {
        return ResponseEntity.ok(decisionService.setRecommendation(id, request.recommendationStatus()));
    }

    // Approve (SELECTED) / Hold (ON_HOLD) / Reject (REJECTED) / Decide later (DEFERRED) / Withdraw.
    @PostMapping("/decisions")
    public ResponseEntity<?> createDecision(@Valid @RequestBody DecisionRequest request) {
        if (!DecisionService.isValidDecision(request.decision())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error",
                            "message", "decision must be one of SELECTED, REJECTED, ON_HOLD, DEFERRED, WITHDRAWN"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(decisionService.createDecision(request));
    }

    // Ek requirement ke decisions ki history (newest first).
    @GetMapping("/decisions")
    public ResponseEntity<?> listDecisions(@RequestParam Integer requirementId) {
        return ResponseEntity.ok(decisionService.listDecisions(requirementId));
    }

    // "Confirm allocation" - yahi par employee_allocations me row banti hai. Kabhi automatic nahi.
    @PostMapping("/allocations")
    public ResponseEntity<?> confirmAllocation(@Valid @RequestBody AllocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(decisionService.confirmAllocation(request));
    }
}
