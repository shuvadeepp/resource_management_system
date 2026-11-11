package tech.csm.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * L&K training repository (FR-GAP-05). Sirf is_approved = 1 aur is_active = 1 wali rows
 * recommendations me use hoti hain.
 */
@Entity
@Table(name = "learning_resources")
public class LearningResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "learning_resource_id")
    private Integer learningResourceId;

    @Column(name = "skill_id")
    private Integer skillId;

    @Column(name = "resource_title")
    private String resourceTitle;

    @Column(name = "resource_type")
    private String resourceType = "COURSE";

    @Column(name = "provider")
    private String provider;

    @Column(name = "resource_url")
    private String resourceUrl;

    @Column(name = "resource_description", columnDefinition = "TEXT")
    private String resourceDescription;

    /** Learner ke paas pehle se kam se kam itna level hona chahiye. */
    @Column(name = "from_proficiency")
    private Integer fromProficiency = 1;

    /** Resource learner ko is level tak le jaata hai. */
    @Column(name = "to_proficiency")
    private Integer toProficiency;

    @Column(name = "estimated_hours")
    private BigDecimal estimatedHours;

    @Column(name = "validation_method")
    private String validationMethod = "KNOWLEDGE_ASSESSMENT";

    @Column(name = "is_approved")
    private Boolean isApproved = false;

    @Column(name = "approved_by")
    private Integer approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Integer getLearningResourceId() { return learningResourceId; }
    public void setLearningResourceId(Integer v) { this.learningResourceId = v; }

    public Integer getSkillId() { return skillId; }
    public void setSkillId(Integer v) { this.skillId = v; }

    public String getResourceTitle() { return resourceTitle; }
    public void setResourceTitle(String v) { this.resourceTitle = v; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String v) { this.resourceType = v; }

    public String getProvider() { return provider; }
    public void setProvider(String v) { this.provider = v; }

    public String getResourceUrl() { return resourceUrl; }
    public void setResourceUrl(String v) { this.resourceUrl = v; }

    public String getResourceDescription() { return resourceDescription; }
    public void setResourceDescription(String v) { this.resourceDescription = v; }

    public Integer getFromProficiency() { return fromProficiency; }
    public void setFromProficiency(Integer v) { this.fromProficiency = v; }

    public Integer getToProficiency() { return toProficiency; }
    public void setToProficiency(Integer v) { this.toProficiency = v; }

    public BigDecimal getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(BigDecimal v) { this.estimatedHours = v; }

    public String getValidationMethod() { return validationMethod; }
    public void setValidationMethod(String v) { this.validationMethod = v; }

    public Boolean getIsApproved() { return isApproved; }
    public void setIsApproved(Boolean v) { this.isApproved = v; }

    public Integer getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Integer v) { this.approvedBy = v; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime v) { this.approvedAt = v; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean v) { this.isActive = v; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer v) { this.createdBy = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
