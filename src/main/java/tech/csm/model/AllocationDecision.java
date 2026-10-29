package tech.csm.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Manager ka decision ek matching_result par: SELECTED / REJECTED / ON_HOLD /
 * DEFERRED / WITHDRAWN. Allocation kabhi automatic nahi hota - allocation_id
 * tabhi set hota hai jab "Confirm allocation" explicitly call ho (see
 * EmployeeAllocation).
 */
@Entity
@Table(name = "allocation_decisions")
public class AllocationDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_decision_id")
    private Integer allocationDecisionId;

    @Column(name = "matching_result_id")
    private Integer matchingResultId;

    @Column(name = "requirement_id")
    private Integer requirementId;

    @Column(name = "allocation_id")
    private Integer allocationId;

    @Column(name = "decision")
    private String decision;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "decision_maker")
    private Integer decisionMaker;

    @Column(name = "decision_date", insertable = false, updatable = false)
    private LocalDateTime decisionDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Integer getAllocationDecisionId() { return allocationDecisionId; }
    public void setAllocationDecisionId(Integer v) { this.allocationDecisionId = v; }

    public Integer getMatchingResultId() { return matchingResultId; }
    public void setMatchingResultId(Integer v) { this.matchingResultId = v; }

    public Integer getRequirementId() { return requirementId; }
    public void setRequirementId(Integer v) { this.requirementId = v; }

    public Integer getAllocationId() { return allocationId; }
    public void setAllocationId(Integer v) { this.allocationId = v; }

    public String getDecision() { return decision; }
    public void setDecision(String v) { this.decision = v; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String v) { this.decisionReason = v; }

    public Integer getDecisionMaker() { return decisionMaker; }
    public void setDecisionMaker(Integer v) { this.decisionMaker = v; }

    public LocalDateTime getDecisionDate() { return decisionDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
