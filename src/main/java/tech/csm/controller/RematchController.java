package tech.csm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import tech.csm.config.ClientIp;
import tech.csm.dto.RematchRequest;
import tech.csm.service.RematchService;
import tech.csm.service.RunCompareService;

/** FR-GAP-08: rematch aur "pehle vs baad" comparison. Purane run ke results kabhi badalte nahi. */
@RestController
@RequestMapping("/api")
public class RematchController {

    private final RematchService rematchService;
    private final RunCompareService compareService;

    public RematchController(RematchService rematchService, RunCompareService compareService) {
        this.rematchService = rematchService;
        this.compareService = compareService;
    }

    // Body optional. ?async=true (default) = 202 turant, run ko poll karo; async=false = poora hone tak ruko.
    @PostMapping("/requirements/{id}/rematch")
    public ResponseEntity<?> rematch(@PathVariable Integer id,
                                     @Valid @RequestBody(required = false) RematchRequest request,
                                     @RequestParam(defaultValue = "true") boolean async,
                                     HttpServletRequest httpRequest) {
        RematchService.Result result = rematchService.rematch(id, request, async, ClientIp.resolve(httpRequest));
        return ResponseEntity.status(result.status()).body(result.body());
    }

    // Naya run vs uska previous_run_id; ?withRunId= se isi requirement ka koi bhi aur run
    @GetMapping("/matching-runs/{id}/compare")
    public ResponseEntity<?> compare(@PathVariable Integer id, @RequestParam(required = false) Integer withRunId) {
        return ResponseEntity.ok(compareService.compare(id, withRunId));
    }
}

