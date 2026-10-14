package tech.csm.model;
 

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_requirements")
public class ProjectRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "requirement_id")
    private Integer requirementId;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "project_code")
    private String projectCode;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "business_domain")
    private String businessDomain;

    @Column(name = "location_id")
    private Integer locationId;

    @Column(name = "work_mode")
    private String workMode;

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "headcount")
    private Integer headcount;

    @Column(name = "priority")
    private String priority;

    @Column(name = "minimum_experience_years")
    private BigDecimal minimumExperienceYears;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (status == null) status = "DRAFT";
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public ProjectRequirement() {}

    public Integer getRequirementId() { return requirementId; }
    public void setRequirementId(Integer requirementId) { this.requirementId = requirementId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBusinessDomain() { return businessDomain; }
    public void setBusinessDomain(String businessDomain) { this.businessDomain = businessDomain; }

    public Integer getLocationId() { return locationId; }
    public void setLocationId(Integer locationId) { this.locationId = locationId; }

    public String getWorkMode() { return workMode; }
    public void setWorkMode(String workMode) { this.workMode = workMode; }

    public Integer getDurationMonths() { return durationMonths; }
    public void setDurationMonths(Integer durationMonths) { this.durationMonths = durationMonths; }

    public Integer getHeadcount() { return headcount; }
    public void setHeadcount(Integer headcount) { this.headcount = headcount; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public BigDecimal getMinimumExperienceYears() { return minimumExperienceYears; }
    public void setMinimumExperienceYears(BigDecimal minimumExperienceYears) { this.minimumExperienceYears = minimumExperienceYears; }

    public LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}