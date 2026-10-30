package tech.csm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.dto.AllocationRequest;
import tech.csm.dto.DecisionRequest;
import tech.csm.exception.ApiException;
import tech.csm.model.AllocationDecision;
import tech.csm.model.EmployeeAllocation;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.User;
import tech.csm.repository.AllocationDecisionRepository;
import tech.csm.repository.EmployeeAllocationRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
import tech.csm.service.MatchingData.Candidate;

/**
 * Shortlist -> Approve / Hold / Reject -> Confirm allocation.
 * Kuch bhi automatic allocate nahi hota - har step explicit call hai.
 * Ek match par naya decision purane ko replace nahi karta: history rehti hai,
 * current_decision = sabse naya.
 */
@Service
public class DecisionService {

    private static final Set<String> VALID_DECISIONS =
            Set.of("SELECTED", "REJECTED", "ON_HOLD", "DEFERRED", "WITHDRAWN");
    private static final Set<String> VALID_RECOMMENDATIONS =
            Set.of("RECOMMENDED", "NOT_RECOMMENDED", "REVIEW_REQUIRED");

    private final MatchingResultRepository matchingResultRepository;
    private final MatchingRunRepository matchingRunRepository;
    private final AllocationDecisionRepository allocationDecisionRepository;
    private final EmployeeAllocationRepository employeeAllocationRepository;
    private final RequirementQueryService queryService;
    private final CandidatePoolService candidatePool;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public DecisionService(MatchingResultRepository matchingResultRepository,
                           MatchingRunRepository matchingRunRepository,
                           AllocationDecisionRepository allocationDecisionRepository,
                           EmployeeAllocationRepository employeeAllocationRepository,
                           RequirementQueryService queryService,
                           CandidatePoolService candidatePool,
                           CurrentUserService currentUser,
                           AuditService audit) {
        this.matchingResultRepository = matchingResultRepository;
        this.matchingRunRepository = matchingRunRepository;
        this.allocationDecisionRepository = allocationDecisionRepository;
        this.employeeAllocationRepository = employeeAllocationRepository;
        this.queryService = queryService;
        this.candidatePool = candidatePool;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public static boolean isValidDecision(String decision) {
        return decision != null && VALID_DECISIONS.contains(decision.trim().toUpperCase());
    }

    // ---------------- recommendation (shortlist / undo) ----------------

    @Transactional
    public MatchingResult shortlist(Integer matchingResultId) {
        return setRecommendation(matchingResultId, "RECOMMENDED");
    }

    @Transactional
    public MatchingResult setRecommendation(Integer matchingResultId, String status) {
        String value = status == null ? "" : status.trim().toUpperCase();
        if (!VALID_RECOMMENDATIONS.contains(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "recommendation_status must be one of RECOMMENDED, NOT_RECOMMENDED, REVIEW_REQUIRED");
        }
        MatchingResult mr = findResult(matchingResultId);
        String old = mr.getRecommendationStatus();
        mr.setRecommendationStatus(value);
        MatchingResult saved = matchingResultRepository.save(mr);
        if (!value.equals(old)) {
            audit.log(currentUser.currentUserId(), AuditService.RECOMMENDATION_CHANGED, "matching_results",
                    matchingResultId, Map.of("recommendation_status", String.valueOf(old)),
                    Map.of("recommendation_status", value), null);
        }
        return saved;
    }

    // ---------------- decisions ----------------

    @Transactional
    public AllocationDecision createDecision(DecisionRequest req) {
        MatchingResult mr = findResult(req.matchingResultId());
        MatchingRun run = matchingRunRepository.findById(mr.getMatchingRunId()).orElse(null);
        if (run == null || !run.getRequirementId().equals(req.requirementId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "matching_result_id " + req.matchingResultId()
                    + " does not belong to requirement_id " + req.requirementId());
        }
        if (allocationDecisionRepository.existsByMatchingResultIdAndAllocationIdIsNotNull(req.matchingResultId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This person's allocation is already confirmed for this match; a new decision is not allowed");
        }

        String decision = req.decision().trim().toUpperCase();
        Integer userId = currentUser.currentUserId();

        AllocationDecision d = new AllocationDecision();
        d.setMatchingResultId(req.matchingResultId());
        d.setRequirementId(req.requirementId());
        d.setDecision(decision);
        d.setDecisionReason(req.decisionReason().trim());
        d.setDecisionMaker(userId);
        d = allocationDecisionRepository.save(d);

        if ("REJECTED".equals(decision) && !"NOT_RECOMMENDED".equals(mr.getRecommendationStatus())) {
            mr.setRecommendationStatus("NOT_RECOMMENDED");
            matchingResultRepository.save(mr);
        }

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("matching_result_id", req.matchingResultId());
        v.put("requirement_id", req.requirementId());
        v.put("decision", decision);
        v.put("decision_reason", d.getDecisionReason());
        audit.log(userId, AuditService.DECISION_MADE, "allocation_decisions", d.getAllocationDecisionId(), null, v, null);
        return d;
    }

    /**
     * History. Purani camelCase keys waise hi (frontend unhe padhta hai) +
     * matching_result_id, employee_id, employee_code, resource_name, decision_maker_name.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDecisions(Integer requirementId) {
        List<AllocationDecision> decisions = allocationDecisionRepository.findByRequirementIdOrderByDecisionDateDesc(requirementId);
        if (decisions.isEmpty()) {
            return List.of();
        }
        Map<Integer, MatchingResult> results = matchingResultRepository.findAllById(
                decisions.stream().map(AllocationDecision::getMatchingResultId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MatchingResult::getMatchingResultId, r -> r));
        Map<Integer, Candidate> candidates = candidatePool.loadByIds(results.values().stream()
                .map(MatchingResult::getEmployeeId).collect(Collectors.toSet()));
        Map<Integer, User> makers = queryService.users(decisions.stream()
                .map(AllocationDecision::getDecisionMaker).collect(Collectors.toSet()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (AllocationDecision d : decisions) {
            MatchingResult mr = results.get(d.getMatchingResultId());
            Candidate c = mr != null ? candidates.get(mr.getEmployeeId()) : null;
            User u = makers.get(d.getDecisionMaker());

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("allocationDecisionId", d.getAllocationDecisionId());
            m.put("allocationId", d.getAllocationId());
            m.put("createdAt", d.getCreatedAt());
            m.put("decision", d.getDecision());
            m.put("decisionDate", d.getDecisionDate());
            m.put("decisionMaker", d.getDecisionMaker());
            m.put("decisionReason", d.getDecisionReason());
            m.put("matchingResultId", d.getMatchingResultId());
            m.put("requirementId", d.getRequirementId());
            m.put("matching_result_id", d.getMatchingResultId());
            m.put("employee_id", mr != null ? mr.getEmployeeId() : null);
            m.put("employee_code", c != null ? c.employeeCode() : null);
            m.put("resource_name", c != null ? c.name() : null);
            m.put("decision_maker_name", u != null ? u.getFullName() : null);
            out.add(m);
        }
        return out;
    }

    // ---------------- allocation ----------------

    @Transactional
    public EmployeeAllocation confirmAllocation(AllocationRequest req) {
        AllocationDecision decision = null;
        if (req.decisionId() != null) {
            decision = allocationDecisionRepository.findById(req.decisionId())
                    .orElseThrow(() -> new NoSuchElementException("Decision not found: " + req.decisionId()));
            if (decision.getAllocationId() != null) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Allocation " + decision.getAllocationId() + " is already confirmed for this decision");
            }
            if (!"SELECTED".equals(decision.getDecision())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Only an approved (SELECTED) decision can be allocated; this one is " + decision.getDecision());
            }
        }

        Integer userId = currentUser.currentUserId();
        EmployeeAllocation a = new EmployeeAllocation();
        a.setEmployeeId(req.employeeId());
        a.setRequirementId(req.requirementId());
        a.setAllocationPercentage(req.allocationPercentage() != null ? req.allocationPercentage() : BigDecimal.valueOf(100));
        a.setAllocationStatus("PROPOSED");
        a.setStartDate(req.startDate());
        a.setEndDate(req.endDate());
        a.setCreatedBy(userId);
        EmployeeAllocation saved = employeeAllocationRepository.save(a);

        if (decision != null) {
            decision.setAllocationId(saved.getAllocationId());
            allocationDecisionRepository.save(decision);
        }

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("employee_id", req.employeeId());
        v.put("requirement_id", req.requirementId());
        v.put("allocation_percentage", saved.getAllocationPercentage());
        v.put("start_date", String.valueOf(req.startDate()));
        v.put("decision_id", req.decisionId());
        audit.log(userId, AuditService.ALLOCATION_CONFIRMED, "employee_allocations", saved.getAllocationId(), null, v, null);
        return saved;
    }

    private MatchingResult findResult(Integer id) {
        return matchingResultRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Matching result not found: " + id));
    }
}
