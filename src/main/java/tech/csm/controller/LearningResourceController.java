package tech.csm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tech.csm.dto.ApprovalRequest;
import tech.csm.dto.LearningResourceRequest;
import tech.csm.service.CandidatePoolService;
import tech.csm.service.CurrentUserService;
import tech.csm.service.LearningCatalogService;

/**
 * L&K training repository (FR-GAP-05). Padhna sab logged-in roles ke liye; create / update / approve
 * sirf ADMIN aur LK_ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/api")
public class LearningResourceController {

    private final LearningCatalogService catalog;
    private final CandidatePoolService candidatePool;
    private final CurrentUserService currentUser;

    public LearningResourceController(LearningCatalogService catalog, CandidatePoolService candidatePool,
                                      CurrentUserService currentUser) {
        this.catalog = catalog;
        this.candidatePool = candidatePool;
        this.currentUser = currentUser;
    }

    // ?skillId=&approved=&active=&q=  (q = title ya provider me text)
    @GetMapping("/learning-resources")
    public ResponseEntity<?> list(@RequestParam(required = false) Integer skillId,
                                  @RequestParam(required = false) Boolean approved,
                                  @RequestParam(required = false) Boolean active,
                                  @RequestParam(required = false) String q) {
        return ResponseEntity.ok(catalog.list(skillId, approved, active, q, candidatePool.skillsById()));
    }

    @GetMapping("/learning-resources/{id}")
    public ResponseEntity<?> get(@PathVariable Integer id) {
        return ResponseEntity.ok(catalog.get(id, candidatePool.skillsById()));
    }

    // Nayi resource unapproved banti hai
    @PostMapping("/learning-resources")
    public ResponseEntity<?> create(@Valid @RequestBody LearningResourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalog.create(request, currentUser.currentUserId(), candidatePool.skillsById()));
    }

    // Poora replace. Approved resource ka content badle to approval hat jaati hai.
    @PutMapping("/learning-resources/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @Valid @RequestBody LearningResourceRequest request) {
        return ResponseEntity.ok(catalog.update(id, request, currentUser.currentUserId(), candidatePool.skillsById()));
    }

    @PutMapping("/learning-resources/{id}/approval")
    public ResponseEntity<?> approval(@PathVariable Integer id, @Valid @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(catalog.setApproval(id, request.approved(), currentUser.currentUserId(),
                candidatePool.skillsById()));
    }
}

