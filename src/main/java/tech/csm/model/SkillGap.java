package tech.csm.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ek matching_result ka ek structured skill gap (FR-GAP-01..03).
 * Java code (AI nahi) run khatam hone par likhta hai. gap_type: MISSING | BELOW_REQUIRED.
 * priority: CRITICAL | HIGH | MEDIUM | LOW.
 */
@Entity
@Table(name = "skill_gaps")
public class SkillGap {

    public static final String MISSING = "MISSING";
    public static final String BELOW_REQUIRED = "BELOW_REQUIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skill_gap_id")
    private Integer skillGapId;

    @Column(name = "matching_result_id")
    private Integer matchingResultId;

    @Column(name = "skill_id")
    private Integer skillId;

    @Column(name = "gap_type")
    private String gapType;

    @Column(name = "is_mandatory")
    private Boolean isMandatory = false;

    @Column(name = "required_proficiency")
    private Integer requiredProficiency;

    /** NULL jab skill missing hai. */
    @Column(name = "current_proficiency")
    private Integer currentProficiency;

    @Column(name = "gap_size")
    private Integer gapSize;

    @Column(name = "priority")
    private String priority = "MEDIUM";

    @Column(name = "priority_reason")
    private String priorityReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Integer getSkillGapId() { return skillGapId; }
    public void setSkillGapId(Integer v) { this.skillGapId = v; }

    public Integer getMatchingResultId() { return matchingResultId; }
    public void setMatchingResultId(Integer v) { this.matchingResultId = v; }

    public Integer getSkillId() { return skillId; }
    public void setSkillId(Integer v) { this.skillId = v; }

    public String getGapType() { return gapType; }
    public void setGapType(String v) { this.gapType = v; }

    public Boolean getIsMandatory() { return isMandatory; }
    public void setIsMandatory(Boolean v) { this.isMandatory = v; }

    public Integer getRequiredProficiency() { return requiredProficiency; }
    public void setRequiredProficiency(Integer v) { this.requiredProficiency = v; }

    public Integer getCurrentProficiency() { return currentProficiency; }
    public void setCurrentProficiency(Integer v) { this.currentProficiency = v; }

    public Integer getGapSize() { return gapSize; }
    public void setGapSize(Integer v) { this.gapSize = v; }

    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }

    public String getPriorityReason() { return priorityReason; }
    public void setPriorityReason(String v) { this.priorityReason = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

