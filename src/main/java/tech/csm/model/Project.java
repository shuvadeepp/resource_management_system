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
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Integer projectId;

    @Column(name = "project_code")
    private String projectCode;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "project_domain")
    private String projectDomain;

    @Column(name = "project_description", columnDefinition = "TEXT")
    private String projectDescription;

    @Column(name = "project_status")
    private String projectStatus = "DRAFT";

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Integer getProjectId() { return projectId; }
    public void setProjectId(Integer v) { this.projectId = v; }

    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String v) { this.projectCode = v; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String v) { this.projectName = v; }

    public String getProjectDomain() { return projectDomain; }
    public void setProjectDomain(String v) { this.projectDomain = v; }

    public String getProjectDescription() { return projectDescription; }
    public void setProjectDescription(String v) { this.projectDescription = v; }

    public String getProjectStatus() { return projectStatus; }
    public void setProjectStatus(String v) { this.projectStatus = v; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer v) { this.createdBy = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
