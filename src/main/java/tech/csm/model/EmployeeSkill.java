package tech.csm.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "employee_skills")
@JsonPropertyOrder({ "name", "proficiency", "experienceYears" })
public class EmployeeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_skill_id")
    private Integer employeeSkillId;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employees employee;

    @ManyToOne
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(name = "is_active")
    private Integer isActive;

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

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ---------- JSON output (teammate ka GET API) ----------

    @JsonProperty("name")
    public String getSkillName() {
        return skill == null ? null : skill.getSkillName();
    }

    @JsonProperty("experienceYears")
    public Integer getExperienceYears() {
        return experienceMonths == null ? null : Math.round(experienceMonths / 12f);
    }

    @JsonProperty("proficiency")
    public Integer getProficiency() {
        try {
            return proficiencyLevel == null ? null : Integer.valueOf(proficiencyLevel.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------- getters / setters ----------

    @JsonIgnore
    public Integer getEmployeeSkillId() { return employeeSkillId; }
    public void setEmployeeSkillId(Integer employeeSkillId) { this.employeeSkillId = employeeSkillId; }

    @JsonIgnore
    public Employees getEmployee() { return employee; }
    public void setEmployee(Employees employee) { this.employee = employee; }

    @JsonIgnore
    public Skill getSkill() { return skill; }
    public void setSkill(Skill skill) { this.skill = skill; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    @JsonIgnore
    public String getSkillPriority() { return skillPriority; }
    public void setSkillPriority(String skillPriority) { this.skillPriority = skillPriority; }

    @JsonIgnore
    public String getProficiencyLevel() { return proficiencyLevel; }
    public void setProficiencyLevel(String proficiencyLevel) { this.proficiencyLevel = proficiencyLevel; }

    @JsonIgnore
    public Integer getExperienceMonths() { return experienceMonths; }
    public void setExperienceMonths(Integer experienceMonths) { this.experienceMonths = experienceMonths; }

    @JsonIgnore
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }

    @JsonIgnore
    public String getInformationSource() { return informationSource; }
    public void setInformationSource(String informationSource) { this.informationSource = informationSource; }

    @JsonIgnore
    public LocalDateTime getLastSkillUpdatedAt() { return lastSkillUpdatedAt; }
    public void setLastSkillUpdatedAt(LocalDateTime lastSkillUpdatedAt) { this.lastSkillUpdatedAt = lastSkillUpdatedAt; }

    @JsonIgnore
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @JsonIgnore
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}