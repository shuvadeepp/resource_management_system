package tech.csm.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.csm.model.MatchingRun;
import tech.csm.repository.AllocationDecisionRepository;
import tech.csm.repository.EmployeesRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
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
    private final MatchingRunRepository matchingRunRepository;

    public DashboardController(EmployeesRepository employeesRepository,
                               ProjectRequirementRepository requirementRepository,
                               AllocationDecisionRepository allocationDecisionRepository,
                               ResourceAvailabilityRepository resourceAvailabilityRepository,
                               MatchingResultRepository matchingResultRepository,
                               MatchingRunRepository matchingRunRepository) {
        this.employeesRepository = employeesRepository;
        this.requirementRepository = requirementRepository;
        this.allocationDecisionRepository = allocationDecisionRepository;
        this.resourceAvailabilityRepository = resourceAvailabilityRepository;
        this.matchingResultRepository = matchingResultRepository;
        this.matchingRunRepository = matchingRunRepository;
    }

    // Saari queries ek read-only transaction me (har query ki alag transaction = alag round trips)
    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public ResponseEntity<?> summary() {
        long totalEmployees = employeesRepository.count();

        // COUNT queries - poori rows (aur unke EAGER employee) load nahi karte
        long openRequirements = requirementRepository.countByStatusIn(List.of("OPEN", "MATCHING", "PARTIALLY_FILLED"));

        // pending decisions = shortlisted matches (RECOMMENDED) jinpar abhi tak koi allocation_decisions row nahi bana
        Set<Integer> decidedMatchingResultIds = allocationDecisionRepository.findAll().stream()
                .map(d -> d.getMatchingResultId())
                .collect(Collectors.toSet());
        long pendingDecisions = matchingResultRepository.findByRecommendationStatus("RECOMMENDED").stream()
                .filter(mr -> !decidedMatchingResultIds.contains(mr.getMatchingResultId()))
                .count();

        long availableEmployees = resourceAvailabilityRepository.countByAvailabilityStatusIn(
                List.of("BENCH", "PARTIALLY_AVAILABLE"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total_employees", totalEmployees);
        body.put("open_requirements", openRequirements);
        body.put("pending_decisions", pendingDecisions);
        body.put("available_employees", availableEmployees);

        // Reports ke liye (additive): group-by counts, ek-ek query
        body.put("matches_by_category", counts(matchingResultRepository.countByCategory(),
                List.of("EXCELLENT", "GOOD", "PARTIAL", "LOW")));
        body.put("decisions_by_type", counts(allocationDecisionRepository.countByDecision(),
                List.of("SELECTED", "ON_HOLD", "REJECTED", "DEFERRED", "WITHDRAWN")));
        body.put("runs_by_status", counts(matchingRunRepository.countByStatus(),
                List.of("QUEUED", "RUNNING", "COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED")));
        body.put("recent_runs", recentRuns());
        return ResponseEntity.ok(body);
    }

    /** Har known value ke liye count (0 bhi), DB ke order se independent. */
    private Map<String, Long> counts(List<Object[]> rows, List<String> keys) {
        Map<String, Long> out = new LinkedHashMap<>();
        keys.forEach(k -> out.put(k, 0L));
        for (Object[] row : rows) {
            if (row[0] != null) {
                out.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
            }
        }
        return out;
    }

    private List<Map<String, Object>> recentRuns() {
        List<MatchingRun> runs = matchingRunRepository.findTop5ByOrderByMatchingRunIdDesc();
        if (runs.isEmpty()) {
            return List.of();
        }
        Map<Integer, Long> resultCounts = new HashMap<>();
        for (Object[] row : matchingResultRepository.countByRunIds(
                runs.stream().map(MatchingRun::getMatchingRunId).toList())) {
            resultCounts.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchingRun r : runs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("matching_run_id", r.getMatchingRunId());
            m.put("requirement_id", r.getRequirementId());
            m.put("run_status", r.getRunStatus());
            m.put("execution_date", r.getExecutionDate());
            m.put("completed_at", r.getCompletedAt());
            m.put("result_count", resultCounts.getOrDefault(r.getMatchingRunId(), 0L));
            m.put("error_message", r.getErrorMessage());
            out.add(m);
        }
        return out;
    }
}
