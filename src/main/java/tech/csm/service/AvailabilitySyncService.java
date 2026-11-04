package tech.csm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.model.EmployeeAllocation;
import tech.csm.model.ResourceAvailability;
import tech.csm.repository.EmployeeAllocationRepository;
import tech.csm.repository.EmployeesRepository;
import tech.csm.repository.ResourceAvailabilityRepository;

/**
 * resource_availability ko employee_allocations ke saath sync rakhta hai (confirm, release, nightly job).
 *
 * Base = employee ki sabse nayi non-"[AUTO]" row (HR/seed) - isme bahar ka kaam bhi hai, isliye wo kabhi
 * overwrite nahi hoti. Na ho to BENCH 100%.
 *   capacity = base capacity - (PROPOSED/PLANNED/ACTIVE allocations ka sum), 0 se kam nahi
 *   0 -> ALLOCATED, 100 se kam -> PARTIALLY_AVAILABLE, warna BENCH
 * Har baar data se dobara calculate hota hai (+/- nahi), isliye drift nahi hota. Kuch badla ho tabhi nayi row
 * insert hoti hai aur purani current row ka effective_until = aaj. Base UNAVAILABLE (exit/leave) ho to HR ka
 * hai - chhoote nahi.
 */
@Service
public class AvailabilitySyncService {

    public static final String AUTO_NOTE_PREFIX = "[AUTO] ";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ResourceAvailabilityRepository resourceAvailabilityRepository;
    private final EmployeeAllocationRepository employeeAllocationRepository;
    private final EmployeesRepository employeesRepository;
    private final AuditService audit;

    public AvailabilitySyncService(ResourceAvailabilityRepository resourceAvailabilityRepository,
                                   EmployeeAllocationRepository employeeAllocationRepository,
                                   EmployeesRepository employeesRepository,
                                   AuditService audit) {
        this.resourceAvailabilityRepository = resourceAvailabilityRepository;
        this.employeeAllocationRepository = employeeAllocationRepository;
        this.employeesRepository = employeesRepository;
        this.audit = audit;
    }

    /** Ek employee. Caller ki transaction me chalta hai, taaki abhi save hua allocation bhi dikhe. */
    @Transactional
    public void syncEmployee(Integer employeeId, Integer userId, String reason) {
        syncEmployees(List.of(employeeId), userId, reason);
    }

    /** Nightly / startup: har wo employee jiska kabhi koi allocation raha. Kitni nayi rows bani. */
    @Transactional
    public int syncAllWithAllocations(String reason) {
        return syncEmployees(employeeAllocationRepository.findDistinctEmployeeIds(), null, reason);
    }

    private int syncEmployees(Collection<Integer> employeeIds, Integer userId, String reason) {
        if (employeeIds.isEmpty()) {
            return 0;
        }
        Map<Integer, List<ResourceAvailability>> rowsByEmployee = resourceAvailabilityRepository
                .findByEmployee_EmployeeIdIn(employeeIds).stream()
                .collect(Collectors.groupingBy(ra -> ra.getEmployee().getEmployeeId()));
        Map<Integer, List<EmployeeAllocation>> occupyingByEmployee = new HashMap<>();
        for (EmployeeAllocation a : employeeAllocationRepository.findByEmployeeIdInAndAllocationStatusIn(
                employeeIds, TaggedPeopleService.OCCUPYING_STATUSES)) {
            occupyingByEmployee.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>()).add(a);
        }

        LocalDate today = LocalDate.now();
        int inserted = 0;
        for (Integer employeeId : employeeIds) {
            if (sync(employeeId, rowsByEmployee.getOrDefault(employeeId, List.of()),
                    occupyingByEmployee.getOrDefault(employeeId, List.of()), userId, reason, today)) {
                inserted++;
            }
        }
        return inserted;
    }

    private boolean sync(Integer employeeId, List<ResourceAvailability> rows, List<EmployeeAllocation> occupying,
                         Integer userId, String reason, LocalDate today) {
        Comparator<ResourceAvailability> byId = Comparator.comparing(ResourceAvailability::getAvailabilityId);
        ResourceAvailability current = rows.stream().max(byId).orElse(null);
        ResourceAvailability base = rows.stream().filter(ra -> !isAuto(ra)).max(byId).orElse(null);

        if (base != null && "UNAVAILABLE".equals(base.getAvailabilityStatus())) {
            return false;
        }

        BigDecimal baseCapacity = base != null && base.getAvailableCapacityPercentage() != null
                ? base.getAvailableCapacityPercentage() : HUNDRED;
        BigDecimal booked = occupying.stream()
                .map(EmployeeAllocation::getAllocationPercentage)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal capacity = baseCapacity.subtract(booked).max(BigDecimal.ZERO);

        String status;
        LocalDate availableFrom;
        LocalDate availableUntil;
        Integer durationMonths;
        if (capacity.signum() == 0) {
            status = "ALLOCATED";
            availableFrom = freeAgainOn(occupying, baseCapacity);
            availableUntil = null;
            durationMonths = null;
        } else {
            status = capacity.compareTo(HUNDRED) < 0 ? "PARTIALLY_AVAILABLE" : "BENCH";
            availableFrom = base != null ? toLocalDate(base.getAvailableFrom()) : null;
            availableUntil = base != null ? toLocalDate(base.getAvailableUntil()) : null;
            durationMonths = base != null ? base.getExpectedDurationMonths() : null;
        }

        if (current != null
                && status.equals(current.getAvailabilityStatus())
                && current.getAvailableCapacityPercentage() != null
                && capacity.compareTo(current.getAvailableCapacityPercentage()) == 0
                && Objects.equals(availableFrom, toLocalDate(current.getAvailableFrom()))) {
            return false;
        }

        Map<String, Object> old = new LinkedHashMap<>();
        if (current != null) {
            old.put("availability_id", current.getAvailabilityId());
            old.put("availability_status", current.getAvailabilityStatus());
            old.put("available_capacity_percentage", current.getAvailableCapacityPercentage());
            old.put("available_from", String.valueOf(toLocalDate(current.getAvailableFrom())));
            // chk_availability_effective_dates: effective_until >= effective_from
            LocalDate from = current.getEffectiveFrom();
            current.setEffectiveUntil(from != null && from.isAfter(today) ? from : today);
            resourceAvailabilityRepository.save(current);
        }

        ResourceAvailability ra = new ResourceAvailability();
        ra.setEmployee(employeesRepository.getReferenceById(employeeId));
        ra.setAvailabilityStatus(status);
        ra.setAvailableCapacityPercentage(capacity);
        ra.setAvailableFrom(toDate(availableFrom));
        ra.setAvailableUntil(toDate(availableUntil));
        ra.setExpectedDurationMonths(durationMonths);
        ra.setAvailabilityNotes(AUTO_NOTE_PREFIX + reason + ". Booked in this system: "
                + plain(booked) + "% of base " + plain(baseCapacity) + "%.");
        ra.setEffectiveFrom(today);
        ra.setRecordedBy(userId);
        ResourceAvailability saved = resourceAvailabilityRepository.save(ra);

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("employee_id", employeeId);
        v.put("availability_status", status);
        v.put("available_capacity_percentage", capacity);
        v.put("available_from", String.valueOf(availableFrom));
        v.put("booked_percentage", booked);
        v.put("base_capacity_percentage", baseCapacity);
        audit.log(userId, AuditService.AVAILABILITY_SYNCED, "resource_availability", saved.getAvailabilityId(),
                old.isEmpty() ? null : old, v, reason);
        return true;
    }

    /**
     * Fully booked employee kab wapas free hoga: sab allocations ki aakhri end_date ke agle din.
     * Koi open-ended ho, ya base hi 0% ho (bahar allocated), to pata nahi -> null.
     */
    private static LocalDate freeAgainOn(List<EmployeeAllocation> occupying, BigDecimal baseCapacity) {
        if (baseCapacity.signum() == 0 || occupying.isEmpty()
                || occupying.stream().anyMatch(a -> a.getEndDate() == null)) {
            return null;
        }
        return occupying.stream().map(EmployeeAllocation::getEndDate)
                .max(Comparator.naturalOrder()).get().plusDays(1);
    }

    private static boolean isAuto(ResourceAvailability ra) {
        return ra.getAvailabilityNotes() != null && ra.getAvailabilityNotes().startsWith(AUTO_NOTE_PREFIX);
    }

    private static LocalDate toLocalDate(Date d) {
        if (d == null) {
            return null;
        }
        if (d instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static Date toDate(LocalDate d) {
        return d != null ? java.sql.Date.valueOf(d) : null;
    }

    private static String plain(BigDecimal b) {
        return b.stripTrailingZeros().toPlainString();
    }
}
