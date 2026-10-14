package tech.csm.model;

 

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "required_skills")
public class RequiredSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "required_skill_id")
    private Long requiredSkillId;

    @Column(name = "requirement_id")
    private Long requirementId;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "is_mandatory")
    private Boolean isMandatory;

    @Column(name = "minimum_proficiency")
    private Integer minimumProficiency;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public RequiredSkill() {}

    public Long getRequiredSkillId() { return requiredSkillId; }
    public void setRequiredSkillId(Long requiredSkillId) { this.requiredSkillId = requiredSkillId; }

    public Long getRequirementId() { return requirementId; }
    public void setRequirementId(Long requirementId) { this.requirementId = requirementId; }

    public Long getSkillId() { return skillId; }
    public void setSkillId(Long skillId) { this.skillId = skillId; }

    public Boolean getIsMandatory() { return isMandatory; }
    public void setIsMandatory(Boolean isMandatory) { this.isMandatory = isMandatory; }

    public Integer getMinimumProficiency() { return minimumProficiency; }
    public void setMinimumProficiency(Integer minimumProficiency) { this.minimumProficiency = minimumProficiency; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}