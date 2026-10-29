package tech.csm.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Gemini ke "learning_suggestions" se banta hai - ek row per skill gap
 * suggestion, ek matching_result ke against.
 */
@Entity
@Table(name = "learning_recommendations")
public class LearningRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "learning_recommendation_id")
    private Integer learningRecommendationId;

    @Column(name = "matching_result_id")
    private Integer matchingResultId;

    @Column(name = "skill_id")
    private Integer skillId;

    @Column(name = "current_proficiency")
    private Integer currentProficiency;

    @Column(name = "target_proficiency")
    private Integer targetProficiency;

    @Column(name = "skill_gap_description", columnDefinition = "TEXT")
    private String skillGapDescription;

    @Column(name = "learning_action")
    private String learningAction;

    @Column(name = "learning_resource")
    private String learningResource;

    @Column(name = "resource_type")
    private String resourceType = "COURSE";

    @Column(name = "review_status")
    private String reviewStatus = "PENDING";

    @Column(name = "reviewed_by")
    private Integer reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comments", columnDefinition = "TEXT")
    private String reviewComments;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Integer getLearningRecommendationId() { return learningRecommendationId; }
    public void setLearningRecommendationId(Integer v) { this.learningRecommendationId = v; }

    public Integer getMatchingResultId() { return matchingResultId; }
    public void setMatchingResultId(Integer v) { this.matchingResultId = v; }

    public Integer getSkillId() { return skillId; }
    public void setSkillId(Integer v) { this.skillId = v; }

    public Integer getCurrentProficiency() { return currentProficiency; }
    public void setCurrentProficiency(Integer v) { this.currentProficiency = v; }

    public Integer getTargetProficiency() { return targetProficiency; }
    public void setTargetProficiency(Integer v) { this.targetProficiency = v; }

    public String getSkillGapDescription() { return skillGapDescription; }
    public void setSkillGapDescription(String v) { this.skillGapDescription = v; }

    public String getLearningAction() { return learningAction; }
    public void setLearningAction(String v) { this.learningAction = v; }

    public String getLearningResource() { return learningResource; }
    public void setLearningResource(String v) { this.learningResource = v; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String v) { this.resourceType = v; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String v) { this.reviewStatus = v; }

    public Integer getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Integer v) { this.reviewedBy = v; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime v) { this.reviewedAt = v; }

    public String getReviewComments() { return reviewComments; }
    public void setReviewComments(String v) { this.reviewComments = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
