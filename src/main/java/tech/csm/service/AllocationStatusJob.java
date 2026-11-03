package tech.csm.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tech.csm.model.EmployeeAllocation;
import tech.csm.repository.EmployeeAllocationRepository;

/**
 * employee_allocations ka status dates se (roz 00:05 IST, aur har startup par taaki band server bhi catch-up kare):
 *   end_date aaj se pehle                    -> COMPLETED
 *   start_date <= aaj (aur khatam nahi)      -> ACTIVE
 *   start_date aaj ke baad aur PROPOSED      -> PLANNED
 * PROPOSED purana (legacy) status hai - ACTIVE / PLANNED ki tarah hi promote / complete hota hai, isliye pehle
 * hi run me saari PROPOSED rows PLANNED / ACTIVE / COMPLETED ban jaati hain. Naye allocations PROPOSED nahi bante.
 * COMPLETED / CANCELLED ko kabhi nahi chhoota, aur ACTIVE ko wapas PLANNED nahi kiya jaata.
 */
@Component
public class AllocationStatusJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AllocationStatusJob.class);

    private final EmployeeAllocationRepository employeeAllocationRepository;
    private final AuditService audit;

    public AllocationStatusJob(EmployeeAllocationRepository employeeAllocationRepository, AuditService audit) {
        this.employeeAllocationRepository = employeeAllocationRepository;
        this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        safeSync();
    }

    @Scheduled(cron = "${app.allocations.status-cron:0 5 0 * * *}", zone = "Asia/Kolkata")
    public void nightly() {
        safeSync();
    }

    /** Dates ke hisaab se sahi status. Naye allocation (confirm) par bhi yahi use hota hai. */
    public static String statusFor(EmployeeAllocation a, LocalDate today) {
        if (a.getEndDate() != null && a.getEndDate().isBefore(today)) {
            return "COMPLETED";
        }
        if (!a.getStartDate().isAfter(today)) {
            return "ACTIVE";
        }
        return "ACTIVE".equals(a.getAllocationStatus()) ? "ACTIVE" : "PLANNED";
    }

    /** Kitni rows badli. */
    public int sync(LocalDate today) {
        List<EmployeeAllocation> changed = new ArrayList<>();
        List<Map<String, Object>> changes = new ArrayList<>();
        for (EmployeeAllocation a : employeeAllocationRepository.findByAllocationStatusIn(TaggedPeopleService.OCCUPYING_STATUSES)) {
            String next = statusFor(a, today);
            if (!next.equals(a.getAllocationStatus())) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("allocation_id", a.getAllocationId());
                c.put("from", a.getAllocationStatus());
                c.put("to", next);
                changes.add(c);
                a.setAllocationStatus(next);
                changed.add(a);
            }
        }
        if (changed.isEmpty()) {
            return 0;
        }
        employeeAllocationRepository.saveAll(changed);

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("as_of", today.toString());
        v.put("changes", changes);
        audit.log(null, AuditService.ALLOCATION_STATUS_SYNCED, "employee_allocations", null, null, v, null, null);
        return changed.size();
    }

    private void safeSync() {
        try {
            int count = sync(LocalDate.now());
            if (count > 0) {
                log.info("Allocation status job updated {} allocation(s)", count);
            }
        } catch (Exception ex) {
            log.error("Allocation status job failed", ex);
        }
    }
}
