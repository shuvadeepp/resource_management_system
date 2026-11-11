package tech.csm.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
import tech.csm.service.TaggedPeopleService;

/** Home dashboard: agle 30 din me free logon ki top skills. Read-only. */
@RestController
@RequestMapping("/api/dashboard")
public class HomeBenchSkillsController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int WINDOW_DAYS = 30;

    private final HomeDashboardRepository repository;

    public HomeBenchSkillsController(HomeDashboardRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/bench-skills")
    @Transactional(readOnly = true)
    public ResponseEntity<?> benchSkills(@RequestParam(defaultValue = "4") int limit) {
        int n = Math.max(1, Math.min(limit, 20));
        LocalDate today = LocalDate.now(IST);
        List<Object[]> rows = repository.benchSkills(
                List.of("BENCH", "PARTIALLY_AVAILABLE"),   // same rule as dashboard available_employees
                TaggedPeopleService.OCCUPYING_STATUSES,
                today, today.plusDays(WINDOW_DAYS), PageRequest.of(0, n));

        List<Map<String, Object>> skills = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skill_id", r[0]);
            m.put("skill_name", r[1]);
            m.put("available_count", ((Number) r[2]).longValue());
            skills.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skills", skills);
        return ResponseEntity.ok(body);
    }
}
