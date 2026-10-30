package tech.csm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tech.csm.service.AuditService;

/**
 * Reports screen ke liye audit trail, newest first.
 * entity = audit_logs.entity_name (jaise "allocation_decisions"), recordId = affected_record_id.
 */
@RestController
@RequestMapping("/api")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<?> list(@RequestParam(required = false) String entity,
                                  @RequestParam(required = false) Integer recordId,
                                  @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(auditService.list(entity, recordId, limit));
    }
}
