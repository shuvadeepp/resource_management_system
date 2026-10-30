package tech.csm.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.csm.model.MatchingRun;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;

/**
 * Async run ka status. Frontend har 2 second poll karta hai; run_status
 * COMPLETED / COMPLETED_WITH_ERRORS / FAILED hote hi /requirements/{id}/matches bulao.
 */
@RestController
@RequestMapping("/api")
public class MatchingRunController {

    private final MatchingRunRepository matchingRunRepository;
    private final MatchingResultRepository matchingResultRepository;

    public MatchingRunController(MatchingRunRepository matchingRunRepository,
                                 MatchingResultRepository matchingResultRepository) {
        this.matchingRunRepository = matchingRunRepository;
        this.matchingResultRepository = matchingResultRepository;
    }

    @GetMapping("/matching-runs/{id}")
    public ResponseEntity<?> get(@PathVariable Integer id) {
        MatchingRun run = matchingRunRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Matching run not found: " + id));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("matching_run_id", run.getMatchingRunId());
        body.put("requirement_id", run.getRequirementId());
        body.put("run_status", run.getRunStatus());
        body.put("candidate_pool_size", run.getCandidatePoolSize());
        body.put("execution_date", run.getExecutionDate());
        body.put("completed_at", run.getCompletedAt());
        body.put("error_message", run.getErrorMessage());
        body.put("result_count", (int) matchingResultRepository.countByMatchingRunId(id));
        body.put("algorithm_name", run.getAlgorithmName());
        body.put("model_version", run.getModelVersion());
        return ResponseEntity.ok(body);
    }
}
