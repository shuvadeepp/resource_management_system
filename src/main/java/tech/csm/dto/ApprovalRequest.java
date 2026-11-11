package tech.csm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

/** PUT /api/learning-resources/{id}/approval - approved true = L&K ne approve kiya, false = approval hata diya. */
public record ApprovalRequest(

        @NotNull(message = "approved is required (true or false)")
        @JsonProperty("approved") Boolean approved
) {}

