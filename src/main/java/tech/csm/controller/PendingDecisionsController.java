package tech.csm.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tech.csm.repository.HomeDashboardRepository;

/**
 * Home dashboard: wo log jinpar insaan ko action lena hai - SHORTLISTED (RECOMMENDED, decision nahi)
 * aur APPROVED_PENDING (SELECTED, allocation confirm nahi). Newest first. Read-only.
 */
@RestController
@RequestMapping("/api/decisions")
public class PendingDecisionsController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private record Item(Map<String, Object> json, LocalDateTime waitingSince) {
    }

    private final HomeDashboardRepository repository;

    public PendingDecisionsController(HomeDashboardRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/pending")
    @Transactional(readOnly = true)
    public ResponseEntity<?> pending(@RequestParam(defaultValue = "5") int limit) {
        int n = Math.max(1, Math.min(limit, 50));
        PageRequest top = PageRequest.of(0, n);

        // Har source ke top-N se merged top-N sahi nikalta hai
        List<Item> items = new ArrayList<>();
        repository.shortlisted(top).forEach(r -> items.add(toItem(r, "SHORTLISTED")));
        repository.approvedPending(top).forEach(r -> items.add(toItem(r, "APPROVED_PENDING")));
        items.sort(Comparator.comparing(Item::waitingSince, Comparator.nullsLast(Comparator.reverseOrder())));

        Object[] s1 = repository.shortlistedStats().get(0);
        Object[] s2 = repository.approvedPendingStats().get(0);
        long total = ((Number) s1[0]).longValue() + ((Number) s2[0]).longValue();

        LocalDateTime oldest = null;
        for (Object[] s : List.of(s1, s2)) {
            LocalDateTime t = (LocalDateTime) s[1];
            if (t != null && (oldest == null || t.isBefore(oldest))) {
                oldest = t;
            }
        }
        Long oldestDays = oldest == null ? null
                : Math.max(0, ChronoUnit.DAYS.between(oldest, LocalDateTime.now(IST)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", total);
        body.put("oldest_waiting_days", oldestDays);
        body.put("items", items.stream().limit(n).map(Item::json).toList());
        return ResponseEntity.ok(body);
    }

    /** Row = [mr_id, run_id, requirement_id, employee_id, name, designation, role, project, score, category, waiting_since]. */
    private Item toItem(Object[] r, String status) {
        LocalDateTime since = (LocalDateTime) r[10];
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("matching_result_id", r[0]);
        m.put("matching_run_id", r[1]);
        m.put("requirement_id", r[2]);
        m.put("employee_id", r[3]);
        m.put("employee_name", r[4]);
        m.put("designation", r[5]);
        m.put("role_name", r[6]);
        m.put("project_name", r[7]);
        m.put("overall_score", r[8]);
        m.put("match_category", r[9]);
        m.put("status", status);
        m.put("waiting_since", since);   // IST (DB session + JVM dono Asia/Kolkata)
        return new Item(m, since);
    }
}
