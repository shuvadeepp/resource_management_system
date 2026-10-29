package tech.csm.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ek Gemini matching call ka "run" record - POST /api/requirements jab bhi
 * skills ke saath call hoti hai aur Gemini successfully reply deta hai,
 * ek run banta hai aur uske matches matching_results me save hote hain.
 */
@Entity
@Table(name = "matching_runs")
public class MatchingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "matching_run_id")
    private Integer matchingRunId;

    @Column(name = "requirement_id")
    private Integer requirementId;

    @Column(name = "execution_date", insertable = false, updatable = false)
    private LocalDateTime executionDate;

    @Column(name = "scoring_configuration_version")
    private String scoringConfigurationVersion;

    @Column(name = "algorithm_name")
    private String algorithmName;

    @Column(name = "algorithm_version")
    private String algorithmVersion;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "candidate_pool_size")
    private Integer candidatePoolSize = 0;

    @Column(name = "run_status")
    private String runStatus = "QUEUED";

    @Column(name = "executed_by")
    private Integer executedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public Integer getMatchingRunId() { return matchingRunId; }
    public void setMatchingRunId(Integer v) { this.matchingRunId = v; }

    public Integer getRequirementId() { return requirementId; }
    public void setRequirementId(Integer v) { this.requirementId = v; }

    public LocalDateTime getExecutionDate() { return executionDate; }

    public String getScoringConfigurationVersion() { return scoringConfigurationVersion; }
    public void setScoringConfigurationVersion(String v) { this.scoringConfigurationVersion = v; }

    public String getAlgorithmName() { return algorithmName; }
    public void setAlgorithmName(String v) { this.algorithmName = v; }

    public String getAlgorithmVersion() { return algorithmVersion; }
    public void setAlgorithmVersion(String v) { this.algorithmVersion = v; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String v) { this.modelVersion = v; }

    public Integer getCandidatePoolSize() { return candidatePoolSize; }
    public void setCandidatePoolSize(Integer v) { this.candidatePoolSize = v; }

    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String v) { this.runStatus = v; }

    public Integer getExecutedBy() { return executedBy; }
    public void setExecutedBy(Integer v) { this.executedBy = v; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime v) { this.completedAt = v; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
}
