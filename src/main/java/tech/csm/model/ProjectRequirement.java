package tech.csm.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_requirements")
public class ProjectRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "requirement_id")
    private Integer requirementId;

    @Column(name = "project_id")
    private Integer projectId;

    @Column(name = "requirement_code")
    private String requirementCode;

    @Column(name = "role_name")
    private String roleName;

    @Column(name = "role_description", columnDefinition = "TEXT")
    private String roleDescription;

    @Column(name = "required_headcount")
    private Integer requiredHeadcount = 1;

    @Column(name = "minimum_proficiency_level")
    private Integer minimumProficiencyLevel = 3;

    @Column(name = "minimum_experience_months")
    private Integer minimumExperienceMonths = 0;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "availability_within_days")
    private Integer availabilityWithinDays = 15;

    @Column(name = "project_duration_months")
    private Integer projectDurationMonths;

    @Column(name = "location")
    private String location;

    @Column(name = "work_mode")
    private String workMode = "AS_REQUIRED";

    @Column(name = "requirement_priority")
    private String requirementPriority = "MEDIUM";

    @Column(name = "requirement_owner")
    private Integer requirementOwner;

    @Column(name = "requirement_status")
    private String status = "DRAFT";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Integer getRequirementId() { return requirementId; }
    public void setRequirementId(Integer v) { this.requirementId = v; }

    public Integer getProjectId() { return projectId; }
    public void setProjectId(Integer v) { this.projectId = v; }

    public String getRequirementCode() { return requirementCode; }
    public void setRequirementCode(String v) { this.requirementCode = v; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String v) { this.roleName = v; }

    public String getRoleDescription() { return roleDescription; }
    public void setRoleDescription(String v) { this.roleDescription = v; }

    public Integer getRequiredHeadcount() { return requiredHeadcount; }
    public void setRequiredHeadcount(Integer v) { this.requiredHeadcount = v; }

    public Integer getMinimumProficiencyLevel() { return minimumProficiencyLevel; }
    public void setMinimumProficiencyLevel(Integer v) { this.minimumProficiencyLevel = v; }

    public Integer getMinimumExperienceMonths() { return minimumExperienceMonths; }
    public void setMinimumExperienceMonths(Integer v) { this.minimumExperienceMonths = v; }

    public LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(LocalDate v) { this.joiningDate = v; }

    public Integer getAvailabilityWithinDays() { return availabilityWithinDays; }
    public void setAvailabilityWithinDays(Integer v) { this.availabilityWithinDays = v; }

    public Integer getProjectDurationMonths() { return projectDurationMonths; }
    public void setProjectDurationMonths(Integer v) { this.projectDurationMonths = v; }

    public String getLocation() { return location; }
    public void setLocation(String v) { this.location = v; }

    public String getWorkMode() { return workMode; }
    public void setWorkMode(String v) { this.workMode = v; }

    public String getRequirementPriority() { return requirementPriority; }
    public void setRequirementPriority(String v) { this.requirementPriority = v; }

    public Integer getRequirementOwner() { return requirementOwner; }
    public void setRequirementOwner(Integer v) { this.requirementOwner = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}