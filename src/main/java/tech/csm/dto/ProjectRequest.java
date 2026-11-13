package tech.csm.dto;

import java.time.LocalDate;

/** Body of POST /api/projects (admin adds a project from the browser). */
public class ProjectRequest {

    private String projectCode;
    private String projectName;
    private String projectDomain;
    private String projectDescription;
    private String projectStatus;
    private LocalDate startDate;
    private LocalDate endDate;

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
}
