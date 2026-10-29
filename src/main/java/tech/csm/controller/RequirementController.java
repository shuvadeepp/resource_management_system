package tech.csm.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.csm.dto.RequirementRequest;
import tech.csm.dto.RequirementResponse;
import tech.csm.service.RequirementMatchService;
import tech.csm.service.RequirementQueryService;

@RestController
@RequestMapping("/api")
public class RequirementController {

    private final RequirementMatchService requirementMatchService;
    private final RequirementQueryService requirementQueryService;

    public RequirementController(RequirementMatchService requirementMatchService,
                                 RequirementQueryService requirementQueryService) {
        this.requirementMatchService = requirementMatchService;
        this.requirementQueryService = requirementQueryService;
    }

    @PostMapping("/requirements")
    public ResponseEntity<RequirementResponse> create(@Valid @RequestBody RequirementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requirementMatchService.createAndMatch(request));
    }

    // Saare saved requirements, har ek ke required_skills ke saath.
    @GetMapping("/requirements")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(requirementQueryService.listRequirements());
    }

    // Ek requirement ka detail.
    @GetMapping("/requirements/{id}")
    public ResponseEntity<?> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(requirementQueryService.getRequirement(id));
    }

    // Us requirement ki sabse recent Gemini run ke saare persisted matches
    // (Decisions screen ke liye - bina dobara Gemini call kiye).
    @GetMapping("/requirements/{id}/matches")
    public ResponseEntity<?> getMatches(@PathVariable Integer id) {
        return ResponseEntity.ok(requirementQueryService.getMatches(id));
    }
}