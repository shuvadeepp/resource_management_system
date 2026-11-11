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

/**
 * Skill ka supporting evidence (FRD FR-EMP-07). FR-GAP-07: training ke baad jo evidence record hota hai
 * wo learning_recommendation_id se us recommendation se jud jaata hai jisne use paida kiya.
 */
@Entity
@Table(name = "skill_evidence")
public class SkillEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id")
    private Integer evidenceId;

    @Column(name = "employee_skill_id")
    private Integer employeeSkillId;

    /** ASSESSMENT | CERTIFICATION | PROJECT_EXPERIENCE | PRACTICAL_ASSIGNMENT | OTHER */
    @Column(name = "evidence_type")
    private String evidenceType;

    @Column(name = "evidence_title")
    private String evidenceTitle;

    @Column(name = "evidence_description", columnDefinition = "TEXT")
    private String evidenceDescription;

    @Column(name = "score")
    private BigDecimal score;

    @Column(name = "maximum_score")
    private BigDecimal maximumScore;

    @Column(name = "evidence_date")
    private LocalDate evidenceDate;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "file_reference")
    private String fileReference;

    /** PENDING | VERIFIED | REJECTED */
    @Column(name = "verification_status")
    private String verificationStatus = "PENDING";

    @Column(name = "verified_by")
    private Integer verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "learning_recommendation_id")
    private Integer learningRecommendationId;

    @Column(name = "recorded_by")
    private Integer recordedBy;

    public Integer getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Integer v) { this.evidenceId = v; }

    public Integer getEmployeeSkillId() { return employeeSkillId; }
    public void setEmployeeSkillId(Integer v) { this.employeeSkillId = v; }

    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String v) { this.evidenceType = v; }

    public String getEvidenceTitle() { return evidenceTitle; }
    public void setEvidenceTitle(String v) { this.evidenceTitle = v; }

    public String getEvidenceDescription() { return evidenceDescription; }
    public void setEvidenceDescription(String v) { this.evidenceDescription = v; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal v) { this.score = v; }

    public BigDecimal getMaximumScore() { return maximumScore; }
    public void setMaximumScore(BigDecimal v) { this.maximumScore = v; }

    public LocalDate getEvidenceDate() { return evidenceDate; }
    public void setEvidenceDate(LocalDate v) { this.evidenceDate = v; }

    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate v) { this.validUntil = v; }

    public String getFileReference() { return fileReference; }
    public void setFileReference(String v) { this.fileReference = v; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String v) { this.verificationStatus = v; }

    public Integer getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(Integer v) { this.verifiedBy = v; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime v) { this.verifiedAt = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public Integer getLearningRecommendationId() { return learningRecommendationId; }
    public void setLearningRecommendationId(Integer v) { this.learningRecommendationId = v; }

    public Integer getRecordedBy() { return recordedBy; }
    public void setRecordedBy(Integer v) { this.recordedBy = v; }
}
