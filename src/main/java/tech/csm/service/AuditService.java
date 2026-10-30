package tech.csm.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import tech.csm.config.ClientIp;
import tech.csm.model.AuditLog;
import tech.csm.model.User;
import tech.csm.repository.AuditLogRepository;
import tech.csm.repository.UserRepo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * audit_logs me likhta hai. Har write apni alag (REQUIRES_NEW) transaction me
 * hoti hai aur koi bhi error yahin pakad liya jaata hai - audit fail hone se
 * main call kabhi fail nahi hona chahiye.
 */
@Service
public class AuditService {

    // action names (audit_logs.action)
    public static final String REQUIREMENT_CREATED = "REQUIREMENT_CREATED";
    public static final String RUN_STARTED = "MATCHING_RUN_STARTED";
    public static final String RUN_COMPLETED = "MATCHING_RUN_COMPLETED";
    public static final String RUN_FAILED = "MATCHING_RUN_FAILED";
    public static final String RECOMMENDATION_CHANGED = "RECOMMENDATION_CHANGED";
    public static final String DECISION_MADE = "DECISION_MADE";
    public static final String ALLOCATION_CONFIRMED = "ALLOCATION_CONFIRMED";
    public static final String LEARNING_REVIEWED = "LEARNING_RECOMMENDATION_REVIEWED";
    public static final String LEARNING_GENERATED = "LEARNING_RECOMMENDATIONS_GENERATED";

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_LIMIT = 500;

    private final AuditLogRepository auditLogRepository;
    private final UserRepo userRepo;
    private final TransactionTemplate requiresNew;
    private final TaskExecutor auditExecutor;

    public AuditService(AuditLogRepository auditLogRepository, UserRepo userRepo,
                        PlatformTransactionManager transactionManager,
                        @Qualifier("auditExecutor") TaskExecutor auditExecutor) {
        this.auditLogRepository = auditLogRepository;
        this.userRepo = userRepo;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.auditExecutor = auditExecutor;
    }

    /**
     * Row request thread me ban jaati hai (IP yahin milta hai), likhi background
     * thread par jaati hai - caller ka response audit ka wait nahi karta.
     */
    public void log(Integer userId, String action, String entityName, Integer recordId,
                    Object oldValues, Object newValues, String remarks) {
        log(userId, action, entityName, recordId, oldValues, newValues, remarks, currentIp());
    }

    /** sourceIp explicit - background thread (async run) me request nahi hota, isliye caller deta hai. */
    public void log(Integer userId, String action, String entityName, Integer recordId,
                    Object oldValues, Object newValues, String remarks, String sourceIp) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUserId(userId);
            entry.setAction(action);
            entry.setEntityName(entityName);
            entry.setAffectedRecordId(recordId);
            entry.setOldValues(oldValues == null ? null : MAPPER.writeValueAsString(oldValues));
            entry.setNewValues(newValues == null ? null : MAPPER.writeValueAsString(newValues));
            entry.setSourceIp(sourceIp);
            entry.setRemarks(remarks);
            auditExecutor.execute(() -> write(entry));
        } catch (Exception ex) {
            log.warn("Audit write failed for {} on {} {}: {}", action, entityName, recordId, ex.getMessage());
        }
    }

    private void write(AuditLog entry) {
        try {
            requiresNew.executeWithoutResult(status -> auditLogRepository.save(entry));
        } catch (Exception ex) {
            log.warn("Audit write failed for {} on {} {}: {}", entry.getAction(), entry.getEntityName(),
                    entry.getAffectedRecordId(), ex.getMessage());
        }
    }

    public List<Map<String, Object>> list(String entity, Integer recordId, Integer limit) {
        int size = limit == null || limit <= 0 ? 50 : Math.min(limit, MAX_LIMIT);
        Pageable page = PageRequest.of(0, size);
        boolean hasEntity = entity != null && !entity.isBlank();

        List<AuditLog> rows;
        if (hasEntity && recordId != null) {
            rows = auditLogRepository.findByEntityNameAndAffectedRecordIdOrderByAuditLogIdDesc(entity, recordId, page);
        } else if (hasEntity) {
            rows = auditLogRepository.findByEntityNameOrderByAuditLogIdDesc(entity, page);
        } else if (recordId != null) {
            rows = auditLogRepository.findByAffectedRecordIdOrderByAuditLogIdDesc(recordId, page);
        } else {
            rows = auditLogRepository.findAllByOrderByAuditLogIdDesc(page);
        }

        Set<Integer> userIds = rows.stream().map(AuditLog::getUserId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Integer, User> users = userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (AuditLog a : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("audit_log_id", a.getAuditLogId());
            m.put("action", a.getAction());
            m.put("entity_name", a.getEntityName());
            m.put("affected_record_id", a.getAffectedRecordId());
            m.put("user_id", a.getUserId());
            User u = a.getUserId() == null ? null : users.get(a.getUserId());
            m.put("user_name", u != null ? u.getFullName() : null);
            m.put("old_values", parse(a.getOldValues()));
            m.put("new_values", parse(a.getNewValues()));
            m.put("action_timestamp", a.getActionTimestamp());
            m.put("source_ip", a.getSourceIp());
            m.put("remarks", a.getRemarks());
            out.add(m);
        }
        return out;
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return ClientIp.resolve(attrs.getRequest()); // X-Forwarded-For / ::1 -> 127.0.0.1
        }
        return null; // background thread
    }
}
