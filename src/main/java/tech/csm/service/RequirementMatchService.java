package tech.csm.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import tech.csm.dto.RequirementRequest;
import tech.csm.dto.RequirementResponse;
import tech.csm.exception.ApiException;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.ProjectRequirement;
import tech.csm.service.MatchingData.Candidate;
import tech.csm.service.MatchingData.CandidateSkill;
import tech.csm.service.MatchingData.FinalMatch;
import tech.csm.service.MatchingData.Gap;
import tech.csm.service.MatchingData.LearningItem;
import tech.csm.service.MatchingData.Outcome;
import tech.csm.service.MatchingData.Scored;
import tech.csm.service.MatchingData.Spec;
import tech.csm.service.MatchingData.Transferable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * POST /api/requirements:
 *  - project_name / joining_date / required_skills me se kuch nahi -> list-only (200, kuch save nahi, Gemini nahi).
 *  - inme se koi bhi aaya -> RequirementValidator: chaaron mandatory (warna 400, saare missing fields).
 *    Valid hone par PEHLE project_requirements + required_skills insert, phir matching run:
 *      default            -> sync: score, store, response me results (201)
 *      ?async=true        -> 202 turant, background me score + store; frontend run ko poll kare.
 */
@Service
public class RequirementMatchService {

    private static final Logger log = LoggerFactory.getLogger(RequirementMatchService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Result(HttpStatus status, Object body) {
    }

    private record Prepared(Spec spec, List<Scored> pool) {
    }

    private final RequirementService requirementService;
    private final CandidatePoolService candidatePool;
    private final MatchingEngine engine;
    private final MatchingPersistenceService persistence;
    private final MatchingWorker worker;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final RequirementValidator validator;

    public RequirementMatchService(RequirementService requirementService,
                                   CandidatePoolService candidatePool,
                                   MatchingEngine engine,
                                   MatchingPersistenceService persistence,
                                   MatchingWorker worker,
                                   CurrentUserService currentUser,
                                   AuditService audit,
                                   RequirementValidator validator) {
        this.requirementService = requirementService;
        this.candidatePool = candidatePool;
        this.engine = engine;
        this.persistence = persistence;
        this.worker = worker;
        this.currentUser = currentUser;
        this.audit = audit;
        this.validator = validator;
    }

    /** clientIp controller ne request thread par nikala hai (insert + har audit row me jaata hai). */
    public Result handle(RequirementRequest r, boolean async, String clientIp) {
        if (!RequirementValidator.isMatchingRequest(r)) {
            return filterOnly(r);
        }
        validator.validate(r); // 400 RequirementValidationException, kuch insert hone se pehle
        return async ? startAsync(r, clientIp) : runSync(r, clientIp);
    }

    // ---------------- list only (nothing saved) ----------------

    private Result filterOnly(RequirementRequest r) {
        List<Candidate> candidates = candidatePool.load(r.designation(), r.department(), r.minimumExperienceYears());

        JsonNode llmResult;
        String message;
        if (candidates.isEmpty()) {
            ObjectNode empty = MAPPER.createObjectNode();
            empty.put("total_qualified_matches", 0);
            empty.putArray("matches");
            empty.put("note", "No employees match the given filters.");
            llmResult = empty;
            message = "No employees found for the given filters";
        } else {
            llmResult = plainList(candidates);
            message = "Employees filtered (no skills given, so no scoring)";
        }

        RequirementResponse body = new RequirementResponse("success", message, null, null,
                candidates.size(), llmResult, false, employeesList(candidates), null, null);
        return new Result(HttpStatus.OK, body);
    }

    // ---------------- sync run ----------------

    private Result runSync(RequirementRequest r, String clientIp) {
        Integer userId = currentUser.currentUserId();
        // Insert pehle, apni transaction me commit - aage kuch bhi fail ho, ye row rehti hai
        ProjectRequirement saved = requirementService.save(r, clientIp);
        auditRequirementCreated(userId, saved, r, clientIp);

        Prepared p = prepare(r);
        MatchingRun run = persistence.createRun(saved.getRequirementId(), p.pool().size(), userId);
        Integer runId = run.getMatchingRunId();
        audit.log(userId, AuditService.RUN_STARTED, "matching_runs", runId, null, Map.of("mode", "sync"), null, clientIp);

        try {
            persistence.markRunning(runId);
            Outcome outcome = engine.score(p.spec(), p.pool());
            List<MatchingResult> results = persistence.saveOutcome(runId, outcome);
            audit.log(userId, AuditService.RUN_COMPLETED, "matching_runs", runId, null,
                    MatchingWorker.runSummary(results.size(), outcome), outcome.aiError(), clientIp);

            String runStatus = MatchingData.AI_FAILED.equals(outcome.aiStatus())
                    ? MatchingPersistenceService.COMPLETED_WITH_ERRORS : MatchingPersistenceService.COMPLETED;
            RequirementResponse body = new RequirementResponse("success", runMessage(outcome),
                    saved.getRequirementId(), saved.getStatus(), outcome.poolSize(),
                    llmResult(outcome, results), true, null, runId, runStatus);
            return new Result(HttpStatus.CREATED, body);
        } catch (RuntimeException ex) {
            log.error("Sync matching run {} failed", runId, ex);
            String message = "Matching failed: " + ex.getClass().getSimpleName();
            persistence.markFailed(runId, message);
            audit.log(userId, AuditService.RUN_FAILED, "matching_runs", runId, null, null, message, clientIp);
            throw ex;
        }
    }

    // ---------------- async run ----------------

    private Result startAsync(RequirementRequest r, String clientIp) {
        Integer userId = currentUser.currentUserId(); // background thread me JWT nahi hota
        ProjectRequirement saved = requirementService.save(r, clientIp);
        auditRequirementCreated(userId, saved, r, clientIp);

        Prepared p = prepare(r);
        MatchingRun run = persistence.createRun(saved.getRequirementId(), p.pool().size(), userId);
        Integer runId = run.getMatchingRunId();
        audit.log(userId, AuditService.RUN_STARTED, "matching_runs", runId, null, Map.of("mode", "async"), null, clientIp);

        try {
            worker.run(runId, p.spec(), p.pool(), userId, clientIp);
        } catch (TaskRejectedException ex) {
            String message = "Server is busy with other matching runs, try again in a minute";
            persistence.markFailed(runId, message);
            audit.log(userId, AuditService.RUN_FAILED, "matching_runs", runId, null, null, message, clientIp);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "accepted");
        body.put("message", "Matching started. Poll GET /api/matching-runs/" + runId
                + " until run_status is COMPLETED, COMPLETED_WITH_ERRORS or FAILED.");
        body.put("requirement_id", saved.getRequirementId());
        body.put("matching_run_id", runId);
        body.put("run_status", MatchingPersistenceService.QUEUED);
        body.put("employees_sent", p.pool().size());
        body.put("saved", true);
        return new Result(HttpStatus.ACCEPTED, body);
    }

    // ---------------- shared ----------------

    private Prepared prepare(RequirementRequest r) {
        CandidatePoolService.PoolData data = candidatePool.loadForMatching(
                r.designation(), r.department(), r.minimumExperienceYears());
        Spec spec = candidatePool.specFromRequest(r.requiredSkills(), r.minimumExperienceYears(), data.skillsByName());
        return new Prepared(spec, engine.buildPool(spec, data.candidates()));
    }

    private String runMessage(Outcome o) {
        if (o.poolSize() == 0) {
            return "Requirement saved. No employee has any of the required (or related) skills";
        }
        return switch (o.aiStatus()) {
            case MatchingData.AI_FAILED -> "Requirement processed. " + o.aiError();
            case MatchingData.AI_DISABLED -> "Requirement processed and scored by rules (AI is turned off)";
            default -> "Requirement processed and matched";
        };
    }

    /** Purana llm_result shape (matches[] ke andar wahi keys) + matching_result_id, rank_position, scored_by. */
    private JsonNode llmResult(Outcome o, List<MatchingResult> saved) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("total_qualified_matches", o.matches().size());
        ArrayNode matches = root.putArray("matches");
        for (int i = 0; i < o.matches().size(); i++) {
            FinalMatch fm = o.matches().get(i);
            Candidate c = fm.scored().candidate();
            ObjectNode m = matches.addObject();
            m.put("employee_id", c.employeeId());
            if (saved != null) {
                m.put("matching_result_id", saved.get(i).getMatchingResultId());
            }
            m.put("employee_code", c.employeeCode());
            m.put("resource_name", c.name());
            m.put("total_experience_years", c.experienceYears());
            m.put("match_score", fm.score());
            m.put("category", fm.category());
            m.put("rank_position", fm.rank());

            ArrayNode matching = m.putArray("matching_skills");
            fm.scored().matchingSkills().forEach(matching::add);
            ArrayNode below = m.putArray("below_required_proficiency");
            ArrayNode missing = m.putArray("missing_skills");
            for (Gap g : fm.scored().gaps()) {
                if (g.missing()) {
                    missing.add(g.skill().skillName());
                } else {
                    ObjectNode b = below.addObject();
                    b.put("skill", g.skill().skillName());
                    b.put("has", g.currentLevel());
                    b.put("needs", g.skill().minProficiency());
                }
            }
            ArrayNode transferable = m.putArray("transferable_skills");
            for (Transferable t : fm.scored().transferable()) {
                ObjectNode tn = transferable.addObject();
                tn.put("skill", t.skill());
                tn.put("related_to", t.relatedTo());
                tn.put("note", "transferable, needs validation");
            }
            m.put("explanation", fm.explanation());
            ArrayNode learning = m.putArray("learning_suggestions");
            for (LearningItem li : fm.learning()) {
                ObjectNode ln = learning.addObject();
                ln.put("skill", li.skill().skillName());
                ln.put("action", li.action());
                ln.put("resource", li.resource());
                ln.put("resource_type", li.resourceType());
            }
            m.put("scored_by", fm.scoredBy());
        }
        root.put("scored_by", MatchingData.AI_USED.equals(o.aiStatus()) ? "AI" : "RULES");
        root.put("note", switch (o.aiStatus()) {
            case MatchingData.AI_FAILED -> o.aiError();
            case MatchingData.AI_DISABLED -> "AI is turned off, scored by rules only";
            default -> "AI recommendations require manager review.";
        });
        return root;
    }

    /** Filter-only llm_result - bilkul purane buildPlainList jaisa shape. */
    private JsonNode plainList(List<Candidate> candidates) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("total_qualified_matches", candidates.size());
        ArrayNode matches = root.putArray("matches");
        for (Candidate c : candidates) {
            ObjectNode m = matches.addObject();
            m.put("employee_id", c.employeeId());
            m.put("employee_code", c.employeeCode());
            m.put("resource_name", c.name());
            m.put("designation", c.designation());
            m.put("department", c.department());
            m.put("total_experience_years", c.experienceYears());
            ArrayNode skills = m.putArray("skills");
            for (CandidateSkill s : c.skills()) {
                ObjectNode sn = skills.addObject();
                sn.put("name", s.skillName());
                sn.put("proficiency", s.proficiency() != null ? String.valueOf(s.proficiency()) : null);
                sn.put("experience_months", s.experienceMonths());
            }
        }
        root.put("note", "Filter result only. Provide required_skills to get match scores.");
        return root;
    }

    /** Naya "employees" field: availability ke saath. */
    private List<Map<String, Object>> employeesList(List<Candidate> candidates) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Candidate c : candidates) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employee_id", c.employeeId());
            m.put("employee_code", c.employeeCode());
            m.put("name", c.name());
            m.put("designation", c.designation());
            m.put("department", c.department());
            m.put("total_experience_years", c.experienceYears());
            List<Map<String, Object>> skills = new ArrayList<>();
            for (CandidateSkill s : c.skills()) {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("name", s.skillName());
                sm.put("proficiency", s.proficiency());
                sm.put("experience_months", s.experienceMonths());
                sm.put("validation_status", s.validationStatus());
                skills.add(sm);
            }
            m.put("skills", skills);
            m.put("availability", availability(c));
            out.add(m);
        }
        return out;
    }

    static Map<String, Object> availability(Candidate c) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("status", c.availabilityStatus());
        a.put("available_from", c.availableFrom());
        a.put("capacity_percentage", c.capacityPercentage());
        return a;
    }

    private void auditRequirementCreated(Integer userId, ProjectRequirement saved, RequirementRequest r, String clientIp) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("project_id", saved.getProjectId());
        v.put("requirement_code", saved.getRequirementCode());
        v.put("role_name", saved.getRoleName());
        v.put("joining_date", String.valueOf(saved.getJoiningDate()));
        v.put("required_skills", r.requiredSkills() == null ? 0 : r.requiredSkills().size());
        audit.log(userId, AuditService.REQUIREMENT_CREATED, "project_requirements",
                saved.getRequirementId(), null, v, null, clientIp);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
