package tech.csm.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "employee_allocations")
public class EmployeeAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id")
    private Integer allocationId;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "requirement_id")
    private Integer requirementId;

    @Column(name = "allocation_percentage")
    private BigDecimal allocationPercentage = BigDecimal.valueOf(100);

    @Column(name = "allocation_status")
    private String allocationStatus = "PROPOSED";

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Integer getAllocationId() { return allocationId; }
    public void setAllocationId(Integer v) { this.allocationId = v; }

    public Integer getEmployeeId() { return employeeId; }
    public void setEmployeeId(Integer v) { this.employeeId = v; }

    public Integer getRequirementId() { return requirementId; }
    public void setRequirementId(Integer v) { this.requirementId = v; }

    public BigDecimal getAllocationPercentage() { return allocationPercentage; }
    public void setAllocationPercentage(BigDecimal v) { this.allocationPercentage = v; }

    public String getAllocationStatus() { return allocationStatus; }
    public void setAllocationStatus(String v) { this.allocationStatus = v; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer v) { this.createdBy = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
