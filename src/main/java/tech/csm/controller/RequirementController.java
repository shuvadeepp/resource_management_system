package tech.csm.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.csm.dto.RequirementRequest;
import tech.csm.dto.RequirementResponse;
import tech.csm.service.RequirementMatchService;

@RestController
@RequestMapping("/api")
public class RequirementController {

    private final RequirementMatchService requirementMatchService;

    public RequirementController(RequirementMatchService requirementMatchService) {
        this.requirementMatchService = requirementMatchService;
    }

    @PostMapping("/requirements")
    public ResponseEntity<RequirementResponse> create(@Valid @RequestBody RequirementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requirementMatchService.createAndMatch(request));
    }
}