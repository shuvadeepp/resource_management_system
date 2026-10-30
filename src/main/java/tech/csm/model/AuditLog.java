package tech.csm.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * audit_logs - kisne kya badla. audit_log_id / affected_record_id DB me BIGINT
 * hain, yahan Integer (project rule; ~2.1 billion rows tak safe).
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id")
    private Integer auditLogId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "action")
    private String action;

    @Column(name = "entity_name")
    private String entityName;

    @Column(name = "affected_record_id")
    private Integer affectedRecordId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "json")
    private String oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "json")
    private String newValues;

    @Column(name = "action_timestamp", insertable = false, updatable = false)
    private LocalDateTime actionTimestamp;

    @Column(name = "source_ip")
    private String sourceIp;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    public Integer getAuditLogId() { return auditLogId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer v) { this.userId = v; }

    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }

    public String getEntityName() { return entityName; }
    public void setEntityName(String v) { this.entityName = v; }

    public Integer getAffectedRecordId() { return affectedRecordId; }
    public void setAffectedRecordId(Integer v) { this.affectedRecordId = v; }

    public String getOldValues() { return oldValues; }
    public void setOldValues(String v) { this.oldValues = v; }

    public String getNewValues() { return newValues; }
    public void setNewValues(String v) { this.newValues = v; }

    public LocalDateTime getActionTimestamp() { return actionTimestamp; }

    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String v) { this.sourceIp = v; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
}
