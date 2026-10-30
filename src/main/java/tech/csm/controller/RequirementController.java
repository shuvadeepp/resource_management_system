package tech.csm.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.csm.config.ClientIp;
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

    // Body optional: bina body / {} / sirf filters = employees list, kuch save nahi.
    // project_name / joining_date / required_skills me se koi bhi -> chaaron mandatory (400 warna),
    // phir insert + matching. ?async=true = 202 turant, run background me.
    // @Valid nahi: saari checks RequirementValidator karta hai, taaki error ek hi format me aaye.
    @PostMapping("/requirements")
    public ResponseEntity<?> create(@RequestBody(required = false) RequirementRequest request,
                                    @RequestParam(defaultValue = "false") boolean async,
                                    HttpServletRequest httpRequest) {
        String clientIp = ClientIp.resolve(httpRequest);
        RequirementMatchService.Result result = requirementMatchService.handle(
                request != null ? request : RequirementRequest.empty(), async, clientIp);
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
