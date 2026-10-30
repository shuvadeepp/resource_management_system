package tech.csm.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.csm.dto.RequirementRequest;
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

    // Body optional: bina body / {} = saare employees, kuch save nahi.
    // ?async=true (skills + project_name + joining_date ke saath) = 202 turant, run background me.
    @PostMapping("/requirements")
    public ResponseEntity<?> create(@Valid @RequestBody(required = false) RequirementRequest request,
                                    @RequestParam(defaultValue = "false") boolean async) {
        RequirementMatchService.Result result = requirementMatchService.handle(
                request != null ? request : RequirementRequest.empty(), async);
        return ResponseEntity.status(result.status()).body(result.body());
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

    // Latest finished run (ya ?runId=) ke saare matches - results + decisions screen ke liye ek hi call.
    @GetMapping("/requirements/{id}/matches")
    public ResponseEntity<?> getMatches(@PathVariable Integer id,
                                        @RequestParam(required = false) Integer runId) {
        return ResponseEntity.ok(requirementQueryService.getMatches(id, runId));
    }
}
