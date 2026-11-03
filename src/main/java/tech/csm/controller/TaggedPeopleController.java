package tech.csm.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tech.csm.dto.ReleaseRequest;
import tech.csm.service.TaggedPeopleAdminService;

/**
 * Tagged People screen: kaun AI search se bahar hai (allocation / pending approval) aur unhe release karna.
 * Release sirf ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/api/tagged-people")
public class TaggedPeopleController {

    private final TaggedPeopleAdminService service;

    public TaggedPeopleController(TaggedPeopleAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(service.list());
    }

    // { "allocation_id": 1, "reason": "..." }  ya  { "decision_id": 5, "reason": "..." }
    @PostMapping("/release")
    public ResponseEntity<?> release(@Valid @RequestBody ReleaseRequest request) {
        return ResponseEntity.ok(service.release(request));
    }
}
