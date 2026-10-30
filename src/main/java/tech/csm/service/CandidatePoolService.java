package tech.csm.service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.dto.RequiredSkillRequest;
import tech.csm.model.EmployeeSkill;
import tech.csm.model.Employees;
import tech.csm.model.RequiredSkill;
import tech.csm.model.ResourceAvailability;
import tech.csm.model.Skill;
import tech.csm.repository.EmployeeSkillRepository;
import tech.csm.repository.EmployeesRepository;
import tech.csm.repository.ResourceAvailabilityRepository;
import tech.csm.repository.SkillRepository;
import tech.csm.service.MatchingData.Candidate;
import tech.csm.service.MatchingData.CandidateSkill;
import tech.csm.service.MatchingData.ReqSkill;
import tech.csm.service.MatchingData.Spec;

/**
 * Employees + unki active skills + availability, fixed number of queries me
 * (employee count se independent): 1 JOIN FETCH + 1 skills batch + 1
 * availability batch, sab ek read-only transaction me.
 */
@Service
public class CandidatePoolService {

    private final EmployeesRepository employeesRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final ResourceAvailabilityRepository resourceAvailabilityRepository;
    private final SkillRepository skillRepository;

    public CandidatePoolService(EmployeesRepository employeesRepository,
                                EmployeeSkillRepository employeeSkillRepository,
                                ResourceAvailabilityRepository resourceAvailabilityRepository,
                                SkillRepository skillRepository) {
        this.employeesRepository = employeesRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.resourceAvailabilityRepository = resourceAvailabilityRepository;
        this.skillRepository = skillRepository;
    }

    /** designation/department substring match (case-insensitive), min experience in years. Sab optional. */
    @Transactional(readOnly = true)
    public List<Candidate> load(String designation, String department, BigDecimal minExperienceYears) {
        List<Employees> filtered = new ArrayList<>();
        for (Employees e : employeesRepository.findAllWithRefs()) {
            if (hasText(designation) && !contains(
                    e.getDesignation() != null ? e.getDesignation().getDesignationName() : null, designation)) {
                continue;
            }
            if (hasText(department) && !contains(
                    e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null, department)) {
                continue;
            }
            if (minExperienceYears != null) {
                Double exp = e.getTotalExperienceYears();
                if (exp == null || exp < minExperienceYears.doubleValue()) {
                    continue;
                }
            }
            filtered.add(e);
        }
        return toCandidates(filtered);
    }

    public record PoolData(Map<String, Skill> skillsByName, List<Candidate> candidates) {
    }

    /** Matching ke liye: skill master + candidates ek hi transaction me (ek round-trip set kam). */
    @Transactional(readOnly = true)
    public PoolData loadForMatching(String designation, String department, BigDecimal minExperienceYears) {
        return new PoolData(skillsByName(), load(designation, department, minExperienceYears));
    }

    @Transactional(readOnly = true)
    public Map<Integer, Candidate> loadByIds(Collection<Integer> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Map.of();
        }
        return toCandidates(employeesRepository.findAllWithRefsByIdIn(employeeIds)).stream()
                .collect(Collectors.toMap(Candidate::employeeId, Function.identity()));
    }

    /** Skill master (38 rows) - lowercase name se. */
    @Transactional(readOnly = true)
    public Map<String, Skill> skillsByName() {
        Map<String, Skill> out = new HashMap<>();
        for (Skill s : skillRepository.findAll()) {
            if (s.getSkillName() != null) {
                out.put(s.getSkillName().trim().toLowerCase(), s);
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<Integer, Skill> skillsById() {
        return skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getSkillId, Function.identity()));
    }

    /** Request body ki skills se spec (skill master se id/category mila ke). */
    public Spec specFromRequest(List<RequiredSkillRequest> requested, BigDecimal minExperienceYears,
                                Map<String, Skill> skillsByName) {
        List<ReqSkill> skills = new ArrayList<>();
        if (requested != null) {
            for (RequiredSkillRequest r : requested) {
                if (r == null || !hasText(r.skillName())) {
                    continue;
                }
                Skill s = skillsByName.get(r.skillName().trim().toLowerCase());
                skills.add(new ReqSkill(
                        s != null ? s.getSkillId() : null,
                        s != null ? s.getSkillName() : r.skillName().trim(),
                        s != null ? s.getSkillCategory() : null,
                        Boolean.TRUE.equals(r.mandatory()),
                        r.minimumProficiency() != null ? r.minimumProficiency() : 1));
            }
        }
        Integer minMonths = minExperienceYears != null
                ? minExperienceYears.multiply(BigDecimal.valueOf(12)).intValue() : null;
        return new Spec(skills, minMonths);
    }

    /** Saved requirement (required_skills rows) se spec. */
    public Spec specFromRequirement(List<RequiredSkill> rows, Integer minExperienceMonths,
                                    Map<Integer, Skill> skillsById) {
        List<ReqSkill> skills = new ArrayList<>();
        for (RequiredSkill rs : rows) {
            Skill s = skillsById.get(rs.getSkillId());
            skills.add(new ReqSkill(
                    rs.getSkillId(),
                    s != null ? s.getSkillName() : "Skill #" + rs.getSkillId(),
                    s != null ? s.getSkillCategory() : null,
                    Boolean.TRUE.equals(rs.getIsMandatory()),
                    rs.getMinimumProficiency() != null ? rs.getMinimumProficiency() : 1));
        }
        return new Spec(skills, minExperienceMonths);
    }

    // ---------------- helpers ----------------

    private List<Candidate> toCandidates(List<Employees> employees) {
        if (employees.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = employees.stream().map(Employees::getEmployeeId).toList();

        Map<Integer, List<CandidateSkill>> skillsByEmployee = new HashMap<>();
        for (EmployeeSkill es : employeeSkillRepository.findActiveWithSkillByEmployeeIds(ids)) {
            Skill s = es.getSkill();
            skillsByEmployee.computeIfAbsent(es.getEmployee().getEmployeeId(), k -> new ArrayList<>())
                    .add(new CandidateSkill(
                            s != null ? s.getSkillId() : null,
                            s != null ? s.getSkillName() : null,
                            s != null ? s.getSkillCategory() : null,
                            es.getProficiency(),
                            es.getExperienceMonths(),
                            es.getValidationStatus()));
        }

        // Ek employee ki kai rows ho sakti hain - sabse nayi (max id) current maani jaati hai
        Map<Integer, ResourceAvailability> availability = new HashMap<>();
        for (ResourceAvailability ra : resourceAvailabilityRepository.findByEmployee_EmployeeIdIn(ids)) {
            Integer empId = ra.getEmployee().getEmployeeId();
            ResourceAvailability current = availability.get(empId);
            if (current == null || ra.getAvailabilityId() > current.getAvailabilityId()) {
                availability.put(empId, ra);
            }
        }

        Map<Integer, Candidate> ordered = new LinkedHashMap<>();
        for (Employees e : employees) {
            ResourceAvailability ra = availability.get(e.getEmployeeId());
            ordered.put(e.getEmployeeId(), new Candidate(
                    e.getEmployeeId(),
                    e.getEmployeeCode(),
                    e.getName(),
                    e.getDesignation() != null ? e.getDesignation().getDesignationName() : null,
                    e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null,
                    e.getTotalExperienceYears(),
                    skillsByEmployee.getOrDefault(e.getEmployeeId(), List.of()),
                    ra != null ? ra.getAvailabilityStatus() : null,
                    ra != null ? formatDate(ra.getAvailableFrom()) : null,
                    ra != null ? ra.getAvailableCapacityPercentage() : null));
        }
        return new ArrayList<>(ordered.values());
    }

    private static String formatDate(Date d) {
        if (d == null) {
            return null;
        }
        if (d instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().toString();
        }
        return new SimpleDateFormat("yyyy-MM-dd").format(d);
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search.trim().toLowerCase());
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
