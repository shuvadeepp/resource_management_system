package tech.csm.model;

  
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_skills")
public class EmployeeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_skill_id")
    private Long employeeSkillId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "skill_priority")
    private String skillPriority;

    @Column(name = "proficiency_level")
    private String proficiencyLevel;

    @Column(name = "experience_months")
    private Integer experienceMonths;

    @Column(name = "validation_status")
    private String validationStatus;

    @Column(name = "information_source")
    private String informationSource;

    @Column(name = "last_skill_updated_at")
    private LocalDateTime lastSkillUpdatedAt;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public EmployeeSkill() {}

    public Long getEmployeeSkillId() { return employeeSkillId; }
    public void setEmployeeSkillId(Long employeeSkillId) { this.employeeSkillId = employeeSkillId; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getSkillId() { return skillId; }
    public void setSkillId(Long skillId) { this.skillId = skillId; }

    public String getSkillPriority() { return skillPriority; }
    public void setSkillPriority(String skillPriority) { this.skillPriority = skillPriority; }

    public String getProficiencyLevel() { return proficiencyLevel; }
    public void setProficiencyLevel(String proficiencyLevel) { this.proficiencyLevel = proficiencyLevel; }

    public Integer getExperienceMonths() { return experienceMonths; }
    public void setExperienceMonths(Integer experienceMonths) { this.experienceMonths = experienceMonths; }

    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }

    public String getInformationSource() { return informationSource; }
    public void setInformationSource(String informationSource) { this.informationSource = informationSource; }

    public LocalDateTime getLastSkillUpdatedAt() { return lastSkillUpdatedAt; }
    public void setLastSkillUpdatedAt(LocalDateTime lastSkillUpdatedAt) { this.lastSkillUpdatedAt = lastSkillUpdatedAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}