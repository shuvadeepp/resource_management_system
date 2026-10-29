package tech.csm.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ek candidate employee ka Gemini match result, ek matching_run ke andar.
 * strengths/skillGaps raw JSON text ke roop me store hote hain (Gemini ke
 * matching_skills/transferable_skills -> strengths, missing_skills/
 * below_required_proficiency -> skill_gaps).
 */
@Entity
@Table(name = "matching_results")
public class MatchingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "matching_result_id")
    private Integer matchingResultId;

    @Column(name = "matching_run_id")
    private Integer matchingRunId;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "overall_score")
    private BigDecimal overallScore;

    @Column(name = "match_category")
    private String matchCategory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths", columnDefinition = "json")
    private String strengths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_gaps", columnDefinition = "json")
    private String skillGaps;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @Column(name = "recommendation_status")
    private String recommendationStatus = "REVIEW_REQUIRED";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Integer getMatchingResultId() { return matchingResultId; }
    public void setMatchingResultId(Integer v) { this.matchingResultId = v; }

    public Integer getMatchingRunId() { return matchingRunId; }
    public void setMatchingRunId(Integer v) { this.matchingRunId = v; }

    public Integer getEmployeeId() { return employeeId; }
    public void setEmployeeId(Integer v) { this.employeeId = v; }

    public BigDecimal getOverallScore() { return overallScore; }
    public void setOverallScore(BigDecimal v) { this.overallScore = v; }

    public String getMatchCategory() { return matchCategory; }
    public void setMatchCategory(String v) { this.matchCategory = v; }

    public String getStrengths() { return strengths; }
    public void setStrengths(String v) { this.strengths = v; }

    public String getSkillGaps() { return skillGaps; }
    public void setSkillGaps(String v) { this.skillGaps = v; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String v) { this.explanation = v; }

    public Integer getRankPosition() { return rankPosition; }
    public void setRankPosition(Integer v) { this.rankPosition = v; }

    public String getRecommendationStatus() { return recommendationStatus; }
    public void setRecommendationStatus(String v) { this.recommendationStatus = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
