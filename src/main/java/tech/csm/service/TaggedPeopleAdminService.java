package tech.csm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.dto.DecisionRequest;
import tech.csm.dto.ReleaseRequest;
import tech.csm.exception.ApiException;
import tech.csm.model.AllocationDecision;
import tech.csm.model.EmployeeAllocation;
import tech.csm.model.Project;
import tech.csm.model.ProjectRequirement;
import tech.csm.repository.AllocationDecisionRepository;
import tech.csm.repository.EmployeeAllocationRepository;
import tech.csm.repository.ProjectRepository;
import tech.csm.repository.ProjectRequirementRepository;
import tech.csm.service.MatchingData.Candidate;

/**
 * Tagged People screen:
 *  - list: aaj ki date par jo tagged hain - sirf confirmed occupying allocations (PROPOSED/PLANNED/ACTIVE) jo
 *    khatam nahi hui (future wali bhi, kyunki wo apne window ki searches block karti hain). Approval akela
 *    (Confirm allocation se pehle) yahan nahi aata - wo AI search ko block bhi nahi karta.
 *  - release: allocation -> shuru nahi hui to CANCELLED, warna COMPLETED (end_date = aaj);
 *    approval -> WITHDRAWN decision (DecisionService se, history rehti hai). Dono ke baad AI search me phir aata hai.
 */
@Service
public class TaggedPeopleAdminService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final EmployeeAllocationRepository employeeAllocationRepository;
    private final AllocationDecisionRepository allocationDecisionRepository;
    private final ProjectRequirementRepository requirementRepository;
    private final ProjectRepository projectRepository;
    private final CandidatePoolService candidatePool;
    private final DecisionService decisionService;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final AvailabilitySyncService availabilitySync;

    public TaggedPeopleAdminService(EmployeeAllocationRepository employeeAllocationRepository,
                                    AllocationDecisionRepository allocationDecisionRepository,
                                    ProjectRequirementRepository requirementRepository,
                                    ProjectRepository projectRepository,
                                    CandidatePoolService candidatePool,
                                    DecisionService decisionService,
                                    CurrentUserService currentUser,
                                    AuditService audit,
                                    AvailabilitySyncService availabilitySync) {
        this.employeeAllocationRepository = employeeAllocationRepository;
        this.allocationDecisionRepository = allocationDecisionRepository;
        this.requirementRepository = requirementRepository;
        this.projectRepository = projectRepository;
        this.candidatePool = candidatePool;
        this.decisionService = decisionService;
        this.currentUser = currentUser;
        this.audit = audit;
        this.availabilitySync = availabilitySync;
    }

    // ---------------- list ----------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        LocalDate today = LocalDate.now();
        Map<Integer, List<EmployeeAllocation>> allocations = new HashMap<>();
        for (EmployeeAllocation a : employeeAllocationRepository.findByAllocationStatusIn(TaggedPeopleService.OCCUPYING_STATUSES)) {
            if (a.getEndDate() == null || !a.getEndDate().isBefore(today)) {
                allocations.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>()).add(a);
            }
        }
        // Sirf confirmed allocations - approval (SELECTED, bina allocation) akele yahan nahi dikhta
        Set<Integer> employeeIds = new HashSet<>(allocations.keySet());
        if (employeeIds.isEmpty()) {
            return List.of();
        }

        Set<Integer> requirementIds = new HashSet<>();
        allocations.values().forEach(l -> l.forEach(a -> requirementIds.add(a.getRequirementId())));
        Map<Integer, ProjectRequirement> requirements = requirementRepository.findAllById(requirementIds).stream()
                .collect(Collectors.toMap(ProjectRequirement::getRequirementId, Function.identity()));
        Map<Integer, Project> projects = projectRepository.findAllById(requirements.values().stream()
                        .map(ProjectRequirement::getProjectId).filter(id -> id != null).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Project::getProjectId, Function.identity()));
        Map<Integer, Candidate> employees = candidatePool.loadByIds(employeeIds);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Integer empId : employeeIds) {
            Candidate c = employees.get(empId);
            List<Map<String, Object>> tags = new ArrayList<>();
            for (EmployeeAllocation a : allocations.getOrDefault(empId, List.of())) {
                tags.add(allocationTag(a, requirements, projects));
            }
            tags.sort(Comparator.comparing(t -> String.valueOf(t.get("start_date"))));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employee_id", empId);
            m.put("employee_code", c != null ? c.employeeCode() : null);
            m.put("resource_name", c != null ? c.name() : null);
            m.put("designation", c != null ? c.designation() : null);
            m.put("department", c != null ? c.department() : null);
            m.put("tags", tags);
            out.add(m);
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("resource_name"))));
        return out;
    }

    // ---------------- release ----------------

    @Transactional
    public Map<String, Object> release(ReleaseRequest req) {
        if ((req.allocationId() == null) == (req.decisionId() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Send exactly one of allocation_id or decision_id");
        }
        return req.allocationId() != null
                ? releaseAllocation(req.allocationId(), req.reason().trim())
                : releaseApproval(req.decisionId(), req.reason().trim());
    }

    private Map<String, Object> releaseAllocation(Integer allocationId, String reason) {
        EmployeeAllocation a = employeeAllocationRepository.findById(allocationId)
                .orElseThrow(() -> new NoSuchElementException("Allocation not found: " + allocationId));
        if (!TaggedPeopleService.OCCUPYING_STATUSES.contains(a.getAllocationStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Allocation " + allocationId + " is already " + a.getAllocationStatus() + ", nothing to release");
        }
        LocalDate today = LocalDate.now();
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("allocation_status", a.getAllocationStatus());
        old.put("end_date", String.valueOf(a.getEndDate()));

        if (a.getStartDate().isAfter(today)) {
            a.setAllocationStatus("CANCELLED"); // abhi shuru hi nahi hui
        } else {
            a.setAllocationStatus("COMPLETED");
            if (a.getEndDate() == null || a.getEndDate().isAfter(today)) {
                a.setEndDate(today);
            }
        }
        employeeAllocationRepository.save(a);

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("allocation_status", a.getAllocationStatus());
        v.put("end_date", String.valueOf(a.getEndDate()));
        Integer userId = currentUser.currentUserId();
        audit.log(userId, AuditService.TAG_RELEASED, "employee_allocations", allocationId,
                old, v, reason);

        withdrawLinkedDecisions(allocationId, userId, reason);

        availabilitySync.syncEmployee(a.getEmployeeId(), userId,
                "Allocation #" + allocationId + " released (" + a.getAllocationStatus() + ")");
        return result("Allocation " + allocationId + " released (" + a.getAllocationStatus()
                + "). Employee will appear in AI search again.");
    }

    /**
     * Allocation release hua to us se linked SELECTED decision bhi isi row me WITHDRAWN (nayi row nahi):
     * allocation_id null, reason/maker/date release ke. allocation_id aur decision saath badalne zaroori hain -
     * sirf allocation_id null hota to "SELECTED bina allocation" = pending approval ban ke wapas dikh jaata.
     * Purani values audit_logs me rehti hain.
     */
    private void withdrawLinkedDecisions(Integer allocationId, Integer userId, String reason) {
        for (AllocationDecision d : allocationDecisionRepository.findByAllocationId(allocationId)) {
            Map<String, Object> old = new LinkedHashMap<>();
            old.put("allocation_id", d.getAllocationId());
            old.put("decision", d.getDecision());
            old.put("decision_reason", d.getDecisionReason());
            old.put("decision_maker", d.getDecisionMaker());
            old.put("decision_date", String.valueOf(d.getDecisionDate()));

            d.setAllocationId(null);
            d.setDecision("WITHDRAWN");
            d.setDecisionReason("Released from Tagged People: " + reason);
            d.setDecisionMaker(userId);
            d.setDecisionDate(LocalDateTime.now(IST));
            allocationDecisionRepository.save(d);

            Map<String, Object> v = new LinkedHashMap<>();
            v.put("allocation_id", null);
            v.put("decision", d.getDecision());
            v.put("decision_reason", d.getDecisionReason());
            v.put("decision_maker", userId);
            v.put("decision_date", String.valueOf(d.getDecisionDate()));
            audit.log(userId, AuditService.TAG_RELEASED, "allocation_decisions", d.getAllocationDecisionId(),
                    old, v, reason);
        }
    }

    private Map<String, Object> releaseApproval(Integer decisionId, String reason) {
        AllocationDecision d = allocationDecisionRepository.findById(decisionId)
                .orElseThrow(() -> new NoSuchElementException("Decision not found: " + decisionId));
        // Wahi decision release ho sakta hai jo abhi bhi pending hai (us employee + requirement ka latest)
        boolean stillPending = TaggedPeopleService.pendingFromRows(allocationDecisionRepository.findAllWithEmployeeId())
                .values().stream().flatMap(List::stream)
                .anyMatch(p -> p.getAllocationDecisionId().equals(decisionId));
        if (!stillPending) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Decision " + decisionId + " is not a pending approval any more (already allocated, withdrawn or replaced)");
        }
        decisionService.createDecision(new DecisionRequest(d.getMatchingResultId(), d.getRequirementId(),
                "WITHDRAWN", "Released from Tagged People: " + reason));
        return result("Approval " + decisionId + " withdrawn. Employee will appear in AI search again.");
    }

    // ---------------- helpers ----------------

    private static Map<String, Object> allocationTag(EmployeeAllocation a, Map<Integer, ProjectRequirement> requirements,
                                                     Map<Integer, Project> projects) {
        boolean full = a.getAllocationPercentage().compareTo(java.math.BigDecimal.valueOf(100)) >= 0;
        Map<String, Object> m = baseTag(full ? TaggedPeopleService.FULLY_ALLOCATED : TaggedPeopleService.PARTIALLY_ALLOCATED,
                a.getRequirementId(), requirements, projects);
        m.put("reason", a.getAllocationPercentage().stripTrailingZeros().toPlainString() + "% allocated ("
                + a.getAllocationStatus() + ")");
        m.put("allocation_id", a.getAllocationId());
        m.put("allocation_percentage", a.getAllocationPercentage());
        m.put("allocation_status", a.getAllocationStatus());
        m.put("decision_id", null);
        m.put("matching_result_id", null);
        m.put("start_date", a.getStartDate().toString());
        m.put("end_date", a.getEndDate() != null ? a.getEndDate().toString() : null);
        return m;
    }

    private static Map<String, Object> baseTag(String tag, Integer requirementId,
                                               Map<Integer, ProjectRequirement> requirements, Map<Integer, Project> projects) {
        ProjectRequirement r = requirements.get(requirementId);
        Project p = r != null && r.getProjectId() != null ? projects.get(r.getProjectId()) : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tag", tag);
        m.put("requirement_id", requirementId);
        m.put("requirement_code", r != null ? r.getRequirementCode() : null);
        m.put("role_name", r != null ? r.getRoleName() : null);
        m.put("project_name", p != null ? p.getProjectName() : null);
        return m;
    }

    private static Map<String, Object> result(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "success");
        m.put("message", message);
        return m;
    }
}
