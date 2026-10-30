package tech.csm.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import tech.csm.model.MatchingResult;
import tech.csm.service.MatchingData.Outcome;
import tech.csm.service.MatchingData.Scored;
import tech.csm.service.MatchingData.Spec;

/**
 * Background run: RUNNING -> score (Gemini optional, transaction ke bahar)
 * -> results ek transaction me -> COMPLETED / COMPLETED_WITH_ERRORS.
 * Koi bhi exception -> FAILED. Run kabhi RUNNING me atka nahi rehna chahiye.
 * userId request thread me hi resolve karke aata hai (yahan SecurityContext nahi hota).
 */
@Component
public class MatchingWorker {

    private static final Logger log = LoggerFactory.getLogger(MatchingWorker.class);

    private final MatchingEngine engine;
    private final MatchingPersistenceService persistence;
    private final AuditService audit;

    public MatchingWorker(MatchingEngine engine, MatchingPersistenceService persistence, AuditService audit) {
        this.engine = engine;
        this.persistence = persistence;
        this.audit = audit;
    }

    // userId aur clientIp request thread me resolve hokar aate hain (yahan request/JWT nahi hota)
    @Async("matchingExecutor")
    public void run(Integer runId, Spec spec, List<Scored> pool, Integer userId, String clientIp) {
        boolean closed = false;
        try {
            persistence.markRunning(runId);
            Outcome outcome = engine.score(spec, pool);
            List<MatchingResult> saved = persistence.saveOutcome(runId, outcome);
            closed = true;
            audit.log(userId, AuditService.RUN_COMPLETED, "matching_runs", runId, null,
                    runSummary(saved.size(), outcome), outcome.aiError(), clientIp);
        } catch (Exception ex) {
            log.error("Matching run {} failed", runId, ex);
            String message = "Matching failed: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() != null ? " - " + truncate(ex.getMessage(), 300) : "");
            persistence.markFailed(runId, message);
            closed = true;
            audit.log(userId, AuditService.RUN_FAILED, "matching_runs", runId, null, null, message, clientIp);
        } finally {
            if (!closed) {
                // markFailed bhi fail hua ho ya koi Error aaya ho - ek aakhri koshish
                try {
                    persistence.markFailed(runId, "Matching stopped unexpectedly");
                } catch (Exception inner) {
                    log.error("Could not mark run {} as FAILED", runId, inner);
                }
            }
        }
    }

    static Map<String, Object> runSummary(int resultCount, Outcome outcome) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("result_count", resultCount);
        m.put("candidate_pool_size", outcome.poolSize());
        m.put("ai_status", outcome.aiStatus());
        return m;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
