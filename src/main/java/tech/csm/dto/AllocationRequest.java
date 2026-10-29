package tech.csm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/allocations body - "Confirm allocation" ke baad call hota hai.
 * decision_id optional hai: diya ho to us allocation_decisions row ka
 * allocation_id bhi is nayi allocation se link ho jaata hai.
 */
public record AllocationRequest(

        @NotNull @JsonProperty("requirement_id") Integer requirementId,
        @NotNull @JsonProperty("employee_id") Integer employeeId,
        @JsonProperty("allocation_percentage") BigDecimal allocationPercentage,
        @NotNull @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("decision_id") Integer decisionId
) {}
