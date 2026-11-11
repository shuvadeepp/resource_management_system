package tech.csm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import tech.csm.dto.RematchRequest;
import tech.csm.exception.ApiException;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.ProjectRequirement;
import tech.csm.repository.MatchingRunRepository;
import tech.csm.repository.RequiredSkillRepository;
import tech.csm.service.MatchingData.Candidate;
import tech.csm.service.MatchingData.Outcome;
import tech.csm.service.MatchingData.Scored;
import tech.csm.service.MatchingData.Spec;

/**
 * FR-GAP-08: training / evidence ke baad usi requirement ko dobara match karna. Naya run banta hai
 * (previous_run_id purane run ko point karta hai); purane run ke results aur gaps waise hi rehte hain.
 * Requirement, required_skills aur joining date saved requirement se aate hain - dobara input nahi chahiye.
 * Employee ki skills abhi ki (updated) hoti hain.
 */
@Service
public class RematchService {

    private static final Logger log = LoggerFactory.getLogger(RematchService.class);
    private static final Set<String> TRIGGERS = Set.of(MatchingPersistenceService.REMATCH_AFTER_LEARNING,
            MatchingPersistenceService.MANUAL_REMATCH);

    public record Result(HttpStatus status, Map<String, Object> body) {
    }

    private final RequirementQueryService queryService;
    private final RequiredSkillRepository requiredSkillRepository;
    private final MatchingRunRepository matchingRunRepository;
    private final CandidatePoolService candidatePool;
    private final TaggedPeopleService taggedPeople;
    private final MatchingEngine engine;
    private final MatchingPersistenceService persistence;
    private final MatchingWorker worker;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public RematchService(RequirementQueryService queryService,
                          RequiredSkillRepository requiredSkillRepository,
                          MatchingRunRepository matchingRunRepository,
                          CandidatePoolService candidatePool,
                          TaggedPeopleService taggedPeople,
                          MatchingEngine engine,
                          MatchingPersistenceService persistence,
                          MatchingWorker worker,
                          CurrentUserService currentUser,
                          AuditService audit) {
        this.queryService = queryService;
        this.requiredSkillRepository = requiredSkillRepository;
        this.matchingRunRepository = matchingRunRepository;
        this.candidatePool = candidatePool;
        this.taggedPeople = taggedPeople;
        this.engine = engine;
        this.persistence = persistence;
        this.worker = worker;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /** async = true: 202 turant, run ko poll karo. async = false: poora hone tak ruko (AI me 1 minute tak lag sakta hai). */
    public Result rematch(Integer requirementId, RematchRequest req, boolean async, String clientIp) {
        RematchRequest r = req != null ? req
                : new RematchRequest(null, null, null, null, null);
        String trigger = r.runTrigger() == null || r.runTrigger().isBlank()
                ? MatchingPersistenceService.REMATCH_AFTER_LEARNING : r.runTrigger().trim().toUpperCase();
        if (!TRIGGERS.contains(trigger)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "run_trigger must be REMATCH_AFTER_LEARNING or MANUAL_REMATCH");
        }

        ProjectRequirement requirement = queryService.findRequirement(requirementId);
        MatchingRun previous = previousRun(requirementId, r.previousRunId());
        if (matchingRunRepository.findByRequirementIdOrderByMatchingRunIdDesc(requirementId).stream()
                .anyMatch(run -> MatchingPersistenceService.QUEUED.equals(run.getRunStatus())
                        || MatchingPersistenceService.RUNNING.equals(run.getRunStatus()))) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "A matching run for this requirement is still running; wait for it to finish before a rematch");
        }

        Prepared p = prepare(requirement, r);
        if (p.spec().skills().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This requirement has no required skills, so there is nothing to match");
        }

        Integer userId = currentUser.currentUserId();
        String note = r.rematchNote() == null || r.rematchNote().isBlank() ? null : r.rematchNote().trim();
        MatchingRun run = persistence.createRun(requirementId, p.pool().size(), userId, trigger,
                previous.getMatchingRunId(), note);
        Integer runId = run.getMatchingRunId();
        Map<String, Object> started = new LinkedHashMap<>();
        started.put("mode", async ? "async" : "sync");
        started.put("run_trigger", trigger);
        started.put("previous_run_id", previous.getMatchingRunId());
        audit.log(userId, AuditService.RUN_STARTED, "matching_runs", runId, null, started, note, clientIp);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requirement_id", requirementId);
        body.put("matching_run_id", runId);
        body.put("previous_run_id", previous.getMatchingRunId());
        body.put("run_trigger", trigger);
        body.put("employees_sent", p.pool().size());

        if (async) {
            try {
                worker.run(runId, p.spec(), p.pool(), userId, clientIp);
            } catch (TaskRejectedException ex) {
                String message = "Server is busy with other matching runs, try again in a minute";
                persistence.markFailed(runId, message);
                audit.log(userId, AuditService.RUN_FAILED, "matching_runs", runId, null, null, message, clientIp);
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message);
            }
            body.put("status", "accepted");
            body.put("run_status", MatchingPersistenceService.QUEUED);
            body.put("message", "Rematch started. Poll GET /api/matching-runs/" + runId
                    + " until run_status is COMPLETED, COMPLETED_WITH_ERRORS or FAILED, then call GET /api/matching-runs/"
                    + runId + "/compare to see the change.");
            return new Result(HttpStatus.ACCEPTED, body);
        }

        try {
            persistence.markRunning(runId);
            Outcome outcome = engine.score(p.spec(), p.pool());
            List<MatchingResult> results = persistence.saveOutcome(runId, outcome);
            audit.log(userId, AuditService.RUN_COMPLETED, "matching_runs", runId, null,
                    MatchingWorker.runSummary(results.size(), outcome), outcome.aiError(), clientIp);
            body.put("status", "success");
            body.put("run_status", MatchingData.AI_FAILED.equals(outcome.aiStatus())
                    ? MatchingPersistenceService.COMPLETED_WITH_ERRORS : MatchingPersistenceService.COMPLETED);
            body.put("result_count", results.size());
            body.put("message", "Rematch finished. Call GET /api/matching-runs/" + runId + "/compare to see the change.");
            return new Result(HttpStatus.CREATED, body);
        } catch (RuntimeException ex) {
            log.error("Sync rematch run {} failed", runId, ex);
            String message = "Matching failed: " + ex.getClass().getSimpleName();
            persistence.markFailed(runId, message);
            audit.log(userId, AuditService.RUN_FAILED, "matching_runs", runId, null, null, message, clientIp);
            throw ex;
        }
    }

    // ---------------- helpers ----------------

    private record Prepared(Spec spec, List<Scored> pool) {
    }

    /** previous_run_id diya ho to wahi (isi requirement ka, finished), warna latest finished run. */
    private MatchingRun previousRun(Integer requirementId, Integer previousRunId) {
        MatchingRun run = queryService.resolveRun(requirementId, previousRunId);
        if (run == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This requirement has no finished matching run yet, so there is nothing to rematch");
        }
        if (!RequirementQueryService.FINISHED.contains(run.getRunStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "previous_run_id " + run.getMatchingRunId() + " is " + run.getRunStatus() + "; pick a finished run");
        }
        return run;
    }

    private Prepared prepare(ProjectRequirement requirement, RematchRequest r) {
        var skillsById = candidatePool.skillsById();
        Spec spec = candidatePool.specFromRequirement(
                requiredSkillRepository.findByRequirementId(requirement.getRequirementId()),
                requirement.getMinimumExperienceMonths(), skillsById);
        BigDecimal minYears = requirement.getMinimumExperienceMonths() == null
                || requirement.getMinimumExperienceMonths() <= 0 ? null
                : BigDecimal.valueOf(requirement.getMinimumExperienceMonths())
                        .divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
        List<Candidate> candidates = candidatePool.load(r.designation(), r.department(), minYears);
        LocalDate joining = requirement.getJoiningDate() != null ? requirement.getJoiningDate() : LocalDate.now();
        TaggedPeopleService.Result tagged = taggedPeople.apply(candidates,
                TaggedPeopleService.Window.of(joining, requirement.getProjectDurationMonths()));
        return new Prepared(spec, engine.buildPool(spec, tagged.pool()));
    }
}

