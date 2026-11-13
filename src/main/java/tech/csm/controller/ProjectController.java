package tech.csm.controller;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.csm.dto.ProjectRequest;
import tech.csm.exception.ApiException;
import tech.csm.model.Project;
import tech.csm.repository.ProjectRepository;
import tech.csm.service.CurrentUserService;

/**
 * Adds a project. Listing stays in MainController (GET /api/projects).
 * Access is limited to ADMIN in SecurityConfig.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Set<String> STATUSES =
            Set.of("DRAFT", "PLANNED", "ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED");

    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;

    public ProjectController(ProjectRepository projectRepository, CurrentUserService currentUserService) {
        this.projectRepository = projectRepository;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<Project> create(@RequestBody ProjectRequest req) {
        String code = trim(req.getProjectCode());
        String name = trim(req.getProjectName());
        if (code == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Project code is required");
        }
        if (name == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Project name is required");
        }
        if (code.length() > 50 || name.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Project code or name is too long");
        }

        String status = trim(req.getProjectStatus());
        status = status == null ? "DRAFT" : status.toUpperCase();
        if (!STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown project status: " + status);
        }

        if (req.getStartDate() != null && req.getEndDate() != null
                && req.getEndDate().isBefore(req.getStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date cannot be before the start date");
        }

        if (projectRepository.existsByProjectCodeIgnoreCase(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "A project with code " + code + " already exists");
        }

        Project p = new Project();
        p.setProjectCode(code);
        p.setProjectName(name);
        p.setProjectDomain(trim(req.getProjectDomain()));
        p.setProjectDescription(trim(req.getProjectDescription()));
        p.setProjectStatus(status);
        p.setStartDate(req.getStartDate());
        p.setEndDate(req.getEndDate());
        p.setCreatedBy(currentUserService.currentUserId());

        return ResponseEntity.status(HttpStatus.CREATED).body(projectRepository.save(p));
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
