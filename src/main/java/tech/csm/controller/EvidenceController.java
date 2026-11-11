package tech.csm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tech.csm.dto.EvidenceRequest;
import tech.csm.service.EvidenceService;

/**
 * FR-GAP-07: training / assessment ke baad evidence. Record karna sirf ADMIN, LK_ADMIN, SKILL_REVIEWER,
 * RESOURCE_MANAGER (SecurityConfig). Padhna sab logged-in roles.
 */
@RestController
@RequestMapping("/api")
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @PostMapping("/learning-recommendations/{id}/evidence")
    public ResponseEntity<?> record(@PathVariable Integer id, @Valid @RequestBody EvidenceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evidenceService.record(id, request));
    }

    @GetMapping("/learning-recommendations/{id}/evidence")
    public ResponseEntity<?> list(@PathVariable Integer id) {
        return ResponseEntity.ok(evidenceService.list(id));
    }
}
