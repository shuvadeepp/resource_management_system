package tech.csm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.model.AllocationDecision;
import tech.csm.model.EmployeeAllocation;
import tech.csm.repository.AllocationDecisionRepository;
import tech.csm.repository.EmployeeAllocationRepository;
import tech.csm.service.MatchingData.Candidate;

/**
 * "Tagged people" - requirement ke apne window me jo pehle se busy hain ya kahin approve ho chuke hain.
 * Window = joining_date se joining_date + duration_months tak (last din = +months -1 day);
 * duration_months nahi = open-ended. "Aaj" se kabhi evaluate nahi hota.
 *
 *  (a) Occupying allocations (PROPOSED / PLANNED / ACTIVE - PROPOSED purani rows hain, wo bhi occupy karti hain)
 *      jo window se overlap karein: window ke kisi bhi din milake >= 100% -> FULLY_ALLOCATED, pool se bahar.
 *      Kam ho -> PARTIALLY_ALLOCATED, pool me rehta hai, aur (100 - peak)% free capacity score me jaati hai.
 *      joining_date se pehle khatam / window ke baad shuru hone wali allocations ignore.
 *  (b) Kisi requirement par (employee, requirement) ka latest decision SELECTED aur allocation_id null
 *      -> APPROVED_PENDING. Sirf info tag: hamesha pool me rehta hai. Block / Tagged People sirf confirmed
 *      allocation (Confirm allocation) ke baad - approval akele kisi ko block nahi karta.
 * "Latest" = sabse bada allocation_decision_id: decision_date me IST/UTC dono mix hain, us par order bharosemand nahi.
 */
@Service
public class TaggedPeopleService {

    public static final String FULLY_ALLOCATED = "FULLY_ALLOCATED";
    public static final String PARTIALLY_ALLOCATED = "PARTIALLY_ALLOCATED";
    public static final String APPROVED_PENDING = "APPROVED_PENDING";

    public static final List<String> OCCUPYING_STATUSES = List.of("PROPOSED", "PLANNED", "ACTIVE");

    private static final BigDecimal FULL = BigDecimal.valueOf(100);

    /** to null = open-ended. Dono din inclusive. */
    public record Window(LocalDate from, LocalDate to) {

        public static Window of(LocalDate joiningDate, Integer durationMonths) {
            LocalDate to = durationMonths != null && durationMonths > 0
                    ? joiningDate.plusMonths(durationMonths).minusDays(1) : null;
            return new Window(joiningDate, to);
        }

        boolean overlaps(EmployeeAllocation a) {
            return (a.getEndDate() == null || !a.getEndDate().isBefore(from))
                    && (to == null || !a.getStartDate().isAfter(to));
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", from.toString());
            m.put("to", to != null ? to.toString() : null);
            return m;
        }
    }

    public record Tagged(Candidate candidate, List<Map<String, Object>> tags) {
    }

    /** pool = matching me jaane wale (windowFree set ke saath); excluded / included = tag wale log. */
    public record Result(Window window, List<Candidate> pool, List<Tagged> excluded, List<Tagged> included) {

        public Map<Integer, List<Map<String, Object>>> tagsByEmployee() {
            Map<Integer, List<Map<String, Object>>> out = new HashMap<>();
            for (Tagged t : included) {
                out.put(t.candidate().employeeId(), t.tags());
            }
            return out;
        }
    }

    private record Key(Integer employeeId, Integer requirementId) {
    }

    private final EmployeeAllocationRepository employeeAllocationRepository;
    private final AllocationDecisionRepository allocationDecisionRepository;

    public TaggedPeopleService(EmployeeAllocationRepository employeeAllocationRepository,
                               AllocationDecisionRepository allocationDecisionRepository) {
        this.employeeAllocationRepository = employeeAllocationRepository;
        this.allocationDecisionRepository = allocationDecisionRepository;
    }

    @Transactional(readOnly = true)
    public Result apply(List<Candidate> candidates, Window window) {
        if (candidates.isEmpty()) {
            return new Result(window, List.of(), List.of(), List.of());
        }
        List<Integer> ids = candidates.stream().map(Candidate::employeeId).toList();
        Map<Integer, List<EmployeeAllocation>> busyByEmployee = overlapping(ids, window);
        Map<Integer, List<AllocationDecision>> pendingByEmployee = pendingApprovals(ids);

        List<Candidate> pool = new ArrayList<>();
        List<Tagged> excluded = new ArrayList<>();
        List<Tagged> included = new ArrayList<>();
        for (Candidate c : candidates) {
            List<Map<String, Object>> tags = new ArrayList<>();
            boolean out = false;
            BigDecimal free = FULL;

            List<EmployeeAllocation> busy = busyByEmployee.getOrDefault(c.employeeId(), List.of());
            if (!busy.isEmpty()) {
                BigDecimal peak = peakLoad(busy, window);
                boolean full = peak.compareTo(FULL) >= 0;
                free = full ? BigDecimal.ZERO : FULL.subtract(peak);
                tags.add(allocationTag(full ? FULLY_ALLOCATED : PARTIALLY_ALLOCATED, peak, free, busy));
                out = full;
            }
            for (AllocationDecision d : pendingByEmployee.getOrDefault(c.employeeId(), List.of())) {
                tags.add(approvalTag(d));
            }

            Candidate withFree = c.withWindowFree(free);
            if (out) {
                excluded.add(new Tagged(withFree, tags));
                continue;
            }
            pool.add(withFree);
            if (!tags.isEmpty()) {
                included.add(new Tagged(withFree, tags));
            }
        }
        return new Result(window, pool, excluded, included);
    }

    /** Window ke andar kisi bhi din ka sabse zyada total allocation %. */
    static BigDecimal peakLoad(List<EmployeeAllocation> busy, Window window) {
        TreeMap<LocalDate, BigDecimal> delta = new TreeMap<>();
        for (EmployeeAllocation a : busy) {
            LocalDate start = a.getStartDate().isBefore(window.from()) ? window.from() : a.getStartDate();
            delta.merge(start, a.getAllocationPercentage(), BigDecimal::add);
            if (a.getEndDate() != null) {
                delta.merge(a.getEndDate().plusDays(1), a.getAllocationPercentage().negate(), BigDecimal::add);
            }
        }
        BigDecimal load = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        for (BigDecimal d : delta.values()) {
            load = load.add(d);
            peak = peak.max(load);
        }
        return peak;
    }

    // ---------------- helpers ----------------

    private Map<Integer, List<EmployeeAllocation>> overlapping(List<Integer> ids, Window window) {
        Map<Integer, List<EmployeeAllocation>> out = new HashMap<>();
        for (EmployeeAllocation a : employeeAllocationRepository.findByEmployeeIdInAndAllocationStatusIn(ids, OCCUPYING_STATUSES)) {
            if (window.overlaps(a)) {
                out.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>()).add(a);
            }
        }
        return out;
    }

    private Map<Integer, List<AllocationDecision>> pendingApprovals(List<Integer> ids) {
        return pendingFromRows(allocationDecisionRepository.findWithEmployeeIdByEmployeeIdIn(ids));
    }

    /** rows = [AllocationDecision, employee_id]. Result: employee_id -> pending (latest SELECTED, bina allocation) decisions. */
    static Map<Integer, List<AllocationDecision>> pendingFromRows(List<Object[]> rows) {
        Map<Key, AllocationDecision> latest = new HashMap<>();
        for (Object[] row : rows) {
            AllocationDecision d = (AllocationDecision) row[0];
            latest.merge(new Key((Integer) row[1], d.getRequirementId()), d,
                    (a, b) -> a.getAllocationDecisionId() > b.getAllocationDecisionId() ? a : b);
        }
        Map<Integer, List<AllocationDecision>> out = new HashMap<>();
        latest.forEach((k, d) -> {
            if ("SELECTED".equals(d.getDecision()) && d.getAllocationId() == null) {
                out.computeIfAbsent(k.employeeId(), x -> new ArrayList<>()).add(d);
            }
        });
        return out;
    }

    private static Map<String, Object> allocationTag(String tag, BigDecimal peak, BigDecimal free,
                                                     List<EmployeeAllocation> busy) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EmployeeAllocation a : busy) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("allocation_id", a.getAllocationId());
            m.put("requirement_id", a.getRequirementId());
            m.put("allocation_percentage", a.getAllocationPercentage());
            m.put("allocation_status", a.getAllocationStatus());
            m.put("start_date", a.getStartDate().toString());
            m.put("end_date", a.getEndDate() != null ? a.getEndDate().toString() : null);
            rows.add(m);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tag", tag);
        m.put("reason", FULLY_ALLOCATED.equals(tag)
                ? "Allocated " + plain(peak) + "% during part of the requirement window"
                : "Up to " + plain(peak) + "% allocated during the requirement window, " + plain(free) + "% free");
        m.put("peak_allocation_percentage", peak);
        m.put("free_percentage", free);
        m.put("allocations", rows);
        return m;
    }

    private static Map<String, Object> approvalTag(AllocationDecision d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tag", APPROVED_PENDING);
        m.put("reason", "Approved (SELECTED) for requirement " + d.getRequirementId() + " but not allocated yet");
        m.put("decision_id", d.getAllocationDecisionId());
        m.put("requirement_id", d.getRequirementId());
        m.put("matching_result_id", d.getMatchingResultId());
        return m;
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
