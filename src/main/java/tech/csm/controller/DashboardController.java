package tech.csm.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.csm.repository.AllocationDecisionRepository;
import tech.csm.repository.EmployeesRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.ProjectRequirementRepository;
import tech.csm.repository.ResourceAvailabilityRepository;

/**
 * Home screen ke counts: total employees, open requirements, pending
 * decisions, available employees. Saare numbers DB se hi aate hain - koi
 * cache/stale data nahi.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final EmployeesRepository employeesRepository;
    private final ProjectRequirementRepository requirementRepository;
    private final AllocationDecisionRepository allocationDecisionRepository;
    private final ResourceAvailabilityRepository resourceAvailabilityRepository;
    private final MatchingResultRepository matchingResultRepository;

    public DashboardController(EmployeesRepository employeesRepository,
                               ProjectRequirementRepository requirementRepository,
                               AllocationDecisionRepository allocationDecisionRepository,
                               ResourceAvailabilityRepository resourceAvailabilityRepository,
                               MatchingResultRepository matchingResultRepository) {
        this.employeesRepository = employeesRepository;
        this.requirementRepository = requirementRepository;
        this.allocationDecisionRepository = allocationDecisionRepository;
        this.resourceAvailabilityRepository = resourceAvailabilityRepository;
        this.matchingResultRepository = matchingResultRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary() {
        long totalEmployees = employeesRepository.count();

        long openRequirements = requirementRepository.findAll().stream()
                .filter(r -> "OPEN".equalsIgnoreCase(r.getStatus()) || "MATCHING".equalsIgnoreCase(r.getStatus())
                        || "PARTIALLY_FILLED".equalsIgnoreCase(r.getStatus()))
                .count();

        // pending decisions = shortlisted matches (RECOMMENDED) jinpar abhi tak koi allocation_decisions row nahi bana
        Set<Integer> decidedMatchingResultIds = allocationDecisionRepository.findAll().stream()
                .map(d -> d.getMatchingResultId())
                .collect(Collectors.toSet());
        long pendingDecisions = matchingResultRepository.findByRecommendationStatus("RECOMMENDED").stream()
                .filter(mr -> !decidedMatchingResultIds.contains(mr.getMatchingResultId()))
                .count();

        long availableEmployees = resourceAvailabilityRepository.findAll().stream()
                .filter(a -> "BENCH".equalsIgnoreCase(a.getAvailabilityStatus())
                        || "PARTIALLY_AVAILABLE".equalsIgnoreCase(a.getAvailabilityStatus()))
                .count();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total_employees", totalEmployees);
        body.put("open_requirements", openRequirements);
        body.put("pending_decisions", pendingDecisions);
        body.put("available_employees", availableEmployees);
        return ResponseEntity.ok(body);
    }
}
