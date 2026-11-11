package tech.csm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.exception.ApiException;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.Skill;
import tech.csm.model.SkillGap;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
import tech.csm.repository.SkillGapRepository;
import tech.csm.service.MatchingData.Candidate;

/**
 * FR-GAP-08: "pehle aur baad" - ek rematch run ko usse pehle ke run se compare karna. Dono runs ke results
 * aur gaps stored hain, isliye comparison un employees ki abhi ki skills par depend nahi karta.
 */
@Service
public class RunCompareService {

    private final MatchingRunRepository matchingRunRepository;
    private final MatchingResultRepository matchingResultRepository;
    private final SkillGapRepository skillGapRepository;
    private final CandidatePoolService candidatePool;

    public RunCompareService(MatchingRunRepository matchingRunRepository,
                             MatchingResultRepository matchingResultRepository,
                             SkillGapRepository skillGapRepository,
                             CandidatePoolService candidatePool) {
        this.matchingRunRepository = matchingRunRepository;
        this.matchingResultRepository = matchingResultRepository;
        this.skillGapRepository = skillGapRepository;
        this.candidatePool = candidatePool;
    }

    /** runId = naya run. withRunId na ho to naya run ka previous_run_id. */
    @Transactional(readOnly = true)
    public Map<String, Object> compare(Integer runId, Integer withRunId) {
        MatchingRun newRun = matchingRunRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Matching run not found: " + runId));
        Integer oldId = withRunId != null ? withRunId : newRun.getPreviousRunId();
        if (oldId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Run " + runId + " has no previous run. Pass ?withRunId= to compare it with another run of the same requirement.");
        }
        MatchingRun oldRun = matchingRunRepository.findById(oldId)
                .orElseThrow(() -> new NoSuchElementException("Matching run not found: " + oldId));
        if (!oldRun.getRequirementId().equals(newRun.getRequirementId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Runs " + oldId + " and " + runId + " belong to different requirements");
        }

        Map<Integer, MatchingResult> oldByEmployee = byEmployee(oldRun.getMatchingRunId());
        Map<Integer, MatchingResult> newByEmployee = byEmployee(newRun.getMatchingRunId());
        List<Integer> resultIds = new ArrayList<>();
        oldByEmployee.values().forEach(r -> resultIds.add(r.getMatchingResultId()));
        newByEmployee.values().forEach(r -> resultIds.add(r.getMatchingResultId()));
        Map<Integer, List<SkillGap>> gapsByResult = resultIds.isEmpty() ? Map.of()
                : skillGapRepository.findByMatchingResultIdIn(resultIds).stream()
                        .collect(Collectors.groupingBy(SkillGap::getMatchingResultId));

        Map<Integer, Skill> skills = candidatePool.skillsById();
        Map<Integer, Candidate> people = candidatePool.loadByIds(
                java.util.stream.Stream.concat(oldByEmployee.keySet().stream(), newByEmployee.keySet().stream())
                        .collect(Collectors.toSet()));

        List<Map<String, Object>> rows = new ArrayList<>();
        int improved = 0;
        int declined = 0;
        int unchanged = 0;
        int closedTotal = 0;
        int newGapTotal = 0;
        BigDecimal scoreChangeSum = BigDecimal.ZERO;
        int compared = 0;

        Map<Integer, Boolean> seen = new HashMap<>();
        List<Integer> employeeIds = new ArrayList<>(newByEmployee.keySet());
        oldByEmployee.keySet().stream().filter(id -> !newByEmployee.containsKey(id)).forEach(employeeIds::add);
        for (Integer employeeId : employeeIds) {
            if (seen.put(employeeId, true) != null) {
                continue;
            }
            MatchingResult o = oldByEmployee.get(employeeId);
            MatchingResult n = newByEmployee.get(employeeId);
            Candidate c = people.get(employeeId);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employee_id", employeeId);
            row.put("employee_code", c != null ? c.employeeCode() : null);
            row.put("resource_name", c != null ? c.name() : null);
            row.put("only_in", o == null ? "NEW_RUN" : (n == null ? "OLD_RUN" : null));
            row.put("old_matching_result_id", o != null ? o.getMatchingResultId() : null);
            row.put("new_matching_result_id", n != null ? n.getMatchingResultId() : null);
            row.put("old_score", o != null ? o.getOverallScore() : null);
            row.put("new_score", n != null ? n.getOverallScore() : null);
            row.put("old_category", o != null ? o.getMatchCategory() : null);
            row.put("new_category", n != null ? n.getMatchCategory() : null);
            row.put("old_rank", o != null ? o.getRankPosition() : null);
            row.put("new_rank", n != null ? n.getRankPosition() : null);

            if (o != null && n != null) {
                compared++;
                BigDecimal change = n.getOverallScore().subtract(o.getOverallScore());
                scoreChangeSum = scoreChangeSum.add(change);
                row.put("score_change", change);
                row.put("category_changed", !String.valueOf(o.getMatchCategory()).equals(String.valueOf(n.getMatchCategory())));
                // positive = rank behtar hua (kam number = upar)
                row.put("rank_change", o.getRankPosition() != null && n.getRankPosition() != null
                        ? o.getRankPosition() - n.getRankPosition() : null);
                if (change.signum() > 0) {
                    improved++;
                } else if (change.signum() < 0) {
                    declined++;
                } else {
                    unchanged++;
                }

                List<SkillGap> oldGaps = gapsByResult.getOrDefault(o.getMatchingResultId(), List.of());
                List<SkillGap> newGaps = gapsByResult.getOrDefault(n.getMatchingResultId(), List.of());
                if (hasStoredGaps(o, oldGaps) && hasStoredGaps(n, newGaps)) {
                    List<Map<String, Object>> changes = diffGaps(oldGaps, newGaps, skills);
                    closedTotal += changes.stream().filter(m -> "CLOSED".equals(m.get("status"))).count();
                    newGapTotal += changes.stream().filter(m -> "NEW".equals(m.get("status"))).count();
                    row.put("gaps_comparable", true);
                    row.put("gap_changes", changes);
                } else {
                    row.put("gaps_comparable", false);
                    row.put("gap_changes", List.of());
                }
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("score_change"),
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("employees_compared", compared);
        summary.put("improved", improved);
        summary.put("declined", declined);
        summary.put("unchanged", unchanged);
        summary.put("average_score_change", compared == 0 ? null
                : scoreChangeSum.divide(BigDecimal.valueOf(compared), 2, java.math.RoundingMode.HALF_UP));
        summary.put("gaps_closed", closedTotal);
        summary.put("new_gaps", newGapTotal);
        summary.put("only_in_new_run", rows.stream().filter(m -> "NEW_RUN".equals(m.get("only_in"))).count());
        summary.put("only_in_old_run", rows.stream().filter(m -> "OLD_RUN".equals(m.get("only_in"))).count());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requirement_id", newRun.getRequirementId());
        body.put("new_run", runMap(newRun));
        body.put("old_run", runMap(oldRun));
        body.put("summary", summary);
        body.put("employees", rows);
        return body;
    }

    // ---------------- helpers ----------------

    private Map<Integer, MatchingResult> byEmployee(Integer runId) {
        Map<Integer, MatchingResult> out = new LinkedHashMap<>();
        for (MatchingResult r : matchingResultRepository.findByMatchingRunIdOrderByRankPositionAsc(runId)) {
            out.putIfAbsent(r.getEmployeeId(), r);
        }
        return out;
    }

    /** Result ke gaps stored hain, ya run ke time koi gap tha hi nahi - tab compare bharosemand hai. */
    private static boolean hasStoredGaps(MatchingResult r, List<SkillGap> rows) {
        return !rows.isEmpty() || !SkillGapService.jsonHasGaps(r.getSkillGaps());
    }

    private static Map<String, Object> runMap(MatchingRun run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("matching_run_id", run.getMatchingRunId());
        m.put("run_trigger", run.getRunTrigger());
        m.put("run_status", run.getRunStatus());
        m.put("execution_date", run.getExecutionDate());
        m.put("completed_at", run.getCompletedAt());
        m.put("previous_run_id", run.getPreviousRunId());
        m.put("rematch_note", run.getRematchNote());
        return m;
    }

    /**
     * Skill ke hisaab se gap ka badlaav. CLOSED = ab gap nahi; NEW = ab naya gap; IMPROVED / WORSENED =
     * gap hai par current level badla (missing = 0); UNCHANGED.
     */
    static List<Map<String, Object>> diffGaps(List<SkillGap> oldGaps, List<SkillGap> newGaps, Map<Integer, Skill> skills) {
        Map<Integer, SkillGap> oldBySkill = oldGaps.stream()
                .collect(Collectors.toMap(SkillGap::getSkillId, g -> g, (a, b) -> a, LinkedHashMap::new));
        Map<Integer, SkillGap> newBySkill = newGaps.stream()
                .collect(Collectors.toMap(SkillGap::getSkillId, g -> g, (a, b) -> a, LinkedHashMap::new));
        List<Integer> skillIds = new ArrayList<>(oldBySkill.keySet());
        newBySkill.keySet().stream().filter(id -> !oldBySkill.containsKey(id)).forEach(skillIds::add);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Integer skillId : skillIds) {
            SkillGap o = oldBySkill.get(skillId);
            SkillGap n = newBySkill.get(skillId);
            String status;
            if (n == null) {
                status = "CLOSED";
            } else if (o == null) {
                status = "NEW";
            } else {
                int oldLevel = o.getCurrentProficiency() == null ? 0 : o.getCurrentProficiency();
                int newLevel = n.getCurrentProficiency() == null ? 0 : n.getCurrentProficiency();
                status = newLevel > oldLevel ? "IMPROVED" : (newLevel < oldLevel ? "WORSENED" : "UNCHANGED");
            }
            SkillGap ref = n != null ? n : o;
            Skill skill = skills.get(skillId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skill_id", skillId);
            m.put("skill_name", skill != null ? skill.getSkillName() : null);
            m.put("status", status);
            m.put("mandatory", ref.getIsMandatory());
            m.put("required_level", ref.getRequiredProficiency());
            // gap_type null = us run me ye skill gap nahi thi; level null + gap_type MISSING = skill hi nahi thi
            m.put("old_gap_type", o != null ? o.getGapType() : null);
            m.put("old_level", o != null ? o.getCurrentProficiency() : null);
            m.put("new_gap_type", n != null ? n.getGapType() : null);
            m.put("new_level", n != null ? n.getCurrentProficiency() : null);
            m.put("old_priority", o != null ? o.getPriority() : null);
            m.put("new_priority", n != null ? n.getPriority() : null);
            out.add(m);
        }
        return out;
    }
}

