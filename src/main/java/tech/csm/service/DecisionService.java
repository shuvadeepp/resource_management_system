package tech.csm.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.dto.AllocationRequest;
import tech.csm.dto.DecisionRequest;
import tech.csm.model.AllocationDecision;
import tech.csm.model.EmployeeAllocation;
import tech.csm.model.MatchingResult;
import tech.csm.repository.AllocationDecisionRepository;
import tech.csm.repository.EmployeeAllocationRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.UserRepo;

/**
 * Shortlist -> Approve/Reject/Decide later -> Confirm allocation.
 * Kuch bhi automatic allocate nahi hota - har step explicit call hai.
 */
@Service
public class DecisionService {

    private static final Set<String> VALID_DECISIONS =
            Set.of("SELECTED", "REJECTED", "ON_HOLD", "DEFERRED", "WITHDRAWN");

    private final MatchingResultRepository matchingResultRepository;
    private final AllocationDecisionRepository allocationDecisionRepository;
    private final EmployeeAllocationRepository employeeAllocationRepository;
    private final UserRepo userRepo;

    public DecisionService(MatchingResultRepository matchingResultRepository,
                           AllocationDecisionRepository allocationDecisionRepository,
                           EmployeeAllocationRepository employeeAllocationRepository,
                           UserRepo userRepo) {
        this.matchingResultRepository = matchingResultRepository;
        this.allocationDecisionRepository = allocationDecisionRepository;
        this.employeeAllocationRepository = employeeAllocationRepository;
        this.userRepo = userRepo;
    }

    @Transactional
    public MatchingResult shortlist(Integer matchingResultId) {
        MatchingResult mr = matchingResultRepository.findById(matchingResultId)
                .orElseThrow(() -> new NoSuchElementException("Matching result not found: " + matchingResultId));
        mr.setRecommendationStatus("RECOMMENDED");
        return matchingResultRepository.save(mr);
    }

    public static boolean isValidDecision(String decision) {
        return decision != null && VALID_DECISIONS.contains(decision.trim().toUpperCase());
    }

    @Transactional
    public AllocationDecision createDecision(DecisionRequest req) {
        AllocationDecision d = new AllocationDecision();
        d.setMatchingResultId(req.matchingResultId());
        d.setRequirementId(req.requirementId());
        d.setDecision(req.decision().trim().toUpperCase());
        d.setDecisionReason(req.decisionReason());
        d.setDecisionMaker(currentUserId());
        return allocationDecisionRepository.save(d);
    }

    public List<AllocationDecision> listDecisions(Integer requirementId) {
        return allocationDecisionRepository.findByRequirementIdOrderByDecisionDateDesc(requirementId);
    }

    @Transactional
    public EmployeeAllocation confirmAllocation(AllocationRequest req) {
        EmployeeAllocation a = new EmployeeAllocation();
        a.setEmployeeId(req.employeeId());
        a.setRequirementId(req.requirementId());
        a.setAllocationPercentage(req.allocationPercentage() != null ? req.allocationPercentage() : BigDecimal.valueOf(100));
        a.setAllocationStatus("PROPOSED");
        a.setStartDate(req.startDate());
        a.setEndDate(req.endDate());
        a.setCreatedBy(currentUserId());
        EmployeeAllocation saved = employeeAllocationRepository.save(a);

        if (req.decisionId() != null) {
            allocationDecisionRepository.findById(req.decisionId()).ifPresent(d -> {
                d.setAllocationId(saved.getAllocationId());
                allocationDecisionRepository.save(d);
            });
        }
        return saved;
    }

    private Integer currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) return null;
            return userRepo.findByUserName(auth.getName()).map(u -> u.getUserId()).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }
}
