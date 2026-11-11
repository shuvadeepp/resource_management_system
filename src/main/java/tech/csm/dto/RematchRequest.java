package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;

/**
 * POST /api/requirements/{id}/rematch (FR-GAP-08). Poori body optional.
 * run_trigger: REMATCH_AFTER_LEARNING (default) | MANUAL_REMATCH.
 * previous_run_id: default = is requirement ka latest finished run.
 * designation / department: pehle run ke filters requirement me save nahi hote; chahiye to yahan do.
 */
public record RematchRequest(

        @JsonProperty("run_trigger") String runTrigger,

        @JsonProperty("previous_run_id") Integer previousRunId,

        @Size(max = 500, message = "rematch_note must be at most 500 characters")
        @JsonProperty("rematch_note") String rematchNote,

        @JsonProperty("designation") String designation,

        @JsonProperty("department") String department
) {}

