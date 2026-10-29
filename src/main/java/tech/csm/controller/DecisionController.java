package tech.csm.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tech.csm.dto.AllocationRequest;
import tech.csm.dto.DecisionRequest;
import tech.csm.service.DecisionService;

import java.util.Map;

/**
 * Shortlist -> Approve / Reject / Decide later -> Confirm allocation.
 */
@RestController
@RequestMapping("/api")
public class DecisionController {

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    // "Shortlisted" - matching_results.recommendation_status = RECOMMENDED.
    // Koi allocation_decisions row nahi banti yahan - decision abhi liya nahi hai.
    @PostMapping("/matching-results/{id}/shortlist")
    public ResponseEntity<?> shortlist(@PathVariable Integer id) {
        return ResponseEntity.ok(decisionService.shortlist(id));
    }

    // Approve (SELECTED) / Reject (REJECTED) / Decide later (DEFERRED or ON_HOLD) / Withdraw.
    @PostMapping("/decisions")
    public ResponseEntity<?> createDecision(@Valid @RequestBody DecisionRequest request) {
        if (!DecisionService.isValidDecision(request.decision())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "decision must be one of SELECTED, REJECTED, ON_HOLD, DEFERRED, WITHDRAWN"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(decisionService.createDecision(request));
    }

    // Ek requirement ke decisions ki history.
    @GetMapping("/decisions")
    public ResponseEntity<?> listDecisions(@RequestParam Integer requirementId) {
        return ResponseEntity.ok(decisionService.listDecisions(requirementId));
    }

    // "Confirm allocation" - yahi par employee_allocations me row banti hai.
    // Kabhi automatic nahi - hamesha ye explicit call hi allocate karta hai.
    @PostMapping("/allocations")
    public ResponseEntity<?> confirmAllocation(@Valid @RequestBody AllocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(decisionService.confirmAllocation(request));
    }
}
