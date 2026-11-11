package tech.csm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.dto.EvidenceRequest;
import tech.csm.exception.ApiException;
import tech.csm.model.EmployeeSkill;
import tech.csm.model.LearningRecommendation;
import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.Skill;
import tech.csm.model.SkillEvidence;
import tech.csm.repository.EmployeeSkillRepository;
import tech.csm.repository.EmployeesRepository;
import tech.csm.repository.LearningRecommendationRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
import tech.csm.repository.SkillEvidenceRepository;

/**
 * FR-GAP-07: training ya assessment ke baad authorized user evidence record karta hai. Evidence
 * recommendation se jud jaata hai, employee ki skill (rating / validation status) update hoti hai,
 * aur rematch me wahi nayi skill use hoti hai. Rating ke purane/naye value audit_logs me jaate hain.
 * Sirf apne DB ki employee_skills badalti hai - Tendrils ERP ko kuch nahi.
 */
@Service
public class EvidenceService {

    public static final Set<String> TYPES = Set.of("ASSESSMENT", "CERTIFICATION", "PROJECT_EXPERIENCE",
            "PRACTICAL_ASSIGNMENT", "OTHER");

    private static final List<String> VALIDATION_LADDER = List.of("SELF_REPORTED", "RA_VALIDATED", "ASSESSMENT_VERIFIED");

    private final LearningRecommendationRepository learningRecommendationRepository;
    private final MatchingResultRepository matchingResultRepository;
    private final MatchingRunRepository matchingRunRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final EmployeesRepository employeesRepository;
    private final SkillEvidenceRepository evidenceRepository;
    private final CandidatePoolService candidatePool;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public EvidenceService(LearningRecommendationRepository learningRecommendationRepository,
                           MatchingResultRepository matchingResultRepository,
                           MatchingRunRepository matchingRunRepository,
                           EmployeeSkillRepository employeeSkillRepository,
                           EmployeesRepository employeesRepository,
                           SkillEvidenceRepository evidenceRepository,
                           CandidatePoolService candidatePool,
                           CurrentUserService currentUser,
                           AuditService audit) {
        this.learningRecommendationRepository = learningRecommendationRepository;
        this.matchingResultRepository = matchingResultRepository;
        this.matchingRunRepository = matchingRunRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.employeesRepository = employeesRepository;
        this.evidenceRepository = evidenceRepository;
        this.candidatePool = candidatePool;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> record(Integer learningRecommendationId, EvidenceRequest req) {
        validate(req);
        LearningRecommendation lr = learningRecommendationRepository.findById(learningRecommendationId)
                .orElseThrow(() -> new NoSuchElementException("Learning recommendation not found: " + learningRecommendationId));
        if ("REJECTED".equals(lr.getReviewStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This recommendation was rejected by L&K, so no evidence can be recorded against it");
        }
        MatchingResult mr = matchingResultRepository.findById(lr.getMatchingResultId())
                .orElseThrow(() -> new NoSuchElementException("Matching result not found: " + lr.getMatchingResultId()));
        MatchingRun run = matchingRunRepository.findById(mr.getMatchingRunId())
                .orElseThrow(() -> new NoSuchElementException("Matching run not found: " + mr.getMatchingRunId()));
        Skill skill = candidatePool.skillsById().get(lr.getSkillId());

        Integer userId = currentUser.currentUserId();
        LocalDateTime now = MatchingPersistenceService.nowIst();
        String type = req.evidenceType().trim().toUpperCase();

        // 1. employee ki skill row (active). Nahi hai to nayi rating ke saath banti hai.
        EmployeeSkill es = employeeSkillRepository.findByEmployee_employeeIdAndIsActive(mr.getEmployeeId(), 1).stream()
                .filter(e -> e.getSkill() != null && lr.getSkillId().equals(e.getSkill().getSkillId()))
                .findFirst().orElse(null);
        boolean created = false;
        if (es == null) {
            if (req.newProficiency() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "This employee has no rating for this skill yet; send new_proficiency (1-5) to create it");
            }
            es = newEmployeeSkill(mr.getEmployeeId(), lr.getSkillId(), now);
            created = true;
        }

        Map<String, Object> oldSkill = skillSnapshot(es, created);
        String validatedTo = upgradedValidation(es.getValidationStatus(), type);
        boolean changed = created;
        if (req.newProficiency() != null && !String.valueOf(req.newProficiency()).equals(es.getProficiencyLevel())) {
            es.setProficiencyLevel(String.valueOf(req.newProficiency()));
            changed = true;
        }
        if (!validatedTo.equals(es.getValidationStatus())) {
            es.setValidationStatus(validatedTo);
            changed = true;
        }
        if (changed) {
            if ("ASSESSMENT".equals(type)) {
                es.setInformationSource("ASSESSMENT");
            }
            es.setLastSkillUpdatedAt(now);
            es.setUpdatedAt(now);
            es = employeeSkillRepository.save(es);
            audit.log(userId, AuditService.SKILL_UPDATED, "employee_skills", es.getEmployeeSkillId(),
                    oldSkill, skillSnapshot(es, false),
                    "Evidence from learning recommendation " + learningRecommendationId);
        }

        // 2. evidence row. Recording ke liye authorized role chahiye (SecurityConfig), isliye VERIFIED.
        SkillEvidence ev = new SkillEvidence();
        ev.setEmployeeSkillId(es.getEmployeeSkillId());
        ev.setEvidenceType(type);
        ev.setEvidenceTitle(req.evidenceTitle().trim());
        ev.setEvidenceDescription(blankToNull(req.evidenceDescription()));
        ev.setScore(req.score());
        ev.setMaximumScore(req.maximumScore());
        ev.setEvidenceDate(req.evidenceDate() != null ? req.evidenceDate() : LocalDate.now());
        ev.setValidUntil(req.validUntil());
        ev.setFileReference(blankToNull(req.fileReference()));
        ev.setVerificationStatus("VERIFIED");
        ev.setVerifiedBy(userId);
        ev.setVerifiedAt(now);
        ev.setLearningRecommendationId(learningRecommendationId);
        ev.setRecordedBy(userId);
        ev = evidenceRepository.save(ev);
        audit.log(userId, AuditService.EVIDENCE_RECORDED, "skill_evidence", ev.getEvidenceId(), null,
                evidenceMap(ev), null);

        // 3. recommendation poori maani jaati hai (jab tak mark_completed false na ho)
        boolean markCompleted = req.markCompleted() == null || req.markCompleted();
        if (markCompleted && !"COMPLETED".equals(lr.getReviewStatus())) {
            Map<String, Object> oldRec = new LinkedHashMap<>();
            oldRec.put("review_status", lr.getReviewStatus());
            lr.setReviewStatus("COMPLETED");
            if (lr.getStartedAt() == null) {
                lr.setStartedAt(now);
            }
            lr.setCompletedAt(now);
            lr.setReviewedBy(userId);
            lr.setReviewedAt(now);
            learningRecommendationRepository.save(lr);
            Map<String, Object> newRec = new LinkedHashMap<>();
            newRec.put("review_status", "COMPLETED");
            audit.log(userId, AuditService.LEARNING_REVIEWED, "learning_recommendations", learningRecommendationId,
                    oldRec, newRec, "Completed by recording evidence " + ev.getEvidenceId());
        }

        Map<String, Object> employeeSkill = new LinkedHashMap<>();
        employeeSkill.put("employee_skill_id", es.getEmployeeSkillId());
        employeeSkill.put("employee_id", mr.getEmployeeId());
        employeeSkill.put("skill_name", skill != null ? skill.getSkillName() : null);
        employeeSkill.put("created", created);
        employeeSkill.put("changed", changed);
        employeeSkill.put("old", oldSkill);
        employeeSkill.put("new", skillSnapshot(es, false));

        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("learning_recommendation_id", lr.getLearningRecommendationId());
        rec.put("review_status", lr.getReviewStatus());
        rec.put("completed_at", lr.getCompletedAt());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("evidence", evidenceMap(ev));
        body.put("employee_skill", employeeSkill);
        body.put("learning_recommendation", rec);
        body.put("next_step", "Run POST /api/requirements/" + run.getRequirementId()
                + "/rematch to score again with the updated skill. The previous results stay unchanged.");
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Integer learningRecommendationId) {
        if (!learningRecommendationRepository.existsById(learningRecommendationId)) {
            throw new NoSuchElementException("Learning recommendation not found: " + learningRecommendationId);
        }
        return evidenceRepository.findByLearningRecommendationIdOrderByEvidenceIdDesc(learningRecommendationId)
                .stream().map(EvidenceService::evidenceMap).toList();
    }

    // ---------------- helpers ----------------

    private EmployeeSkill newEmployeeSkill(Integer employeeId, Integer skillId, LocalDateTime now) {
        EmployeeSkill es = new EmployeeSkill();
        es.setEmployee(employeesRepository.getReferenceById(employeeId));
        es.setSkill(candidatePool.skillsById().get(skillId));
        es.setIsActive(1);
        // employee_skills ke NOT NULL columns - entity null insert karta hai, DB default nahi lagta
        es.setSkillPriority("TERTIARY");
        es.setExperienceMonths(0);
        es.setValidationStatus("SELF_REPORTED");
        es.setInformationSource("OTHER");
        es.setCreatedAt(now);
        es.setUpdatedAt(now);
        es.setLastSkillUpdatedAt(now);
        return es;
    }

    private static Map<String, Object> skillSnapshot(EmployeeSkill es, boolean none) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (none) {
            m.put("proficiency", null);
            m.put("validation_status", null);
            m.put("information_source", null);
            return m;
        }
        m.put("proficiency", es.getProficiency());
        m.put("validation_status", es.getValidationStatus());
        m.put("information_source", es.getInformationSource());
        return m;
    }

    /**
     * Validation kabhi neeche nahi jaati. ASSESSMENT evidence -> ASSESSMENT_VERIFIED; baaki evidence
     * (certificate, project, practical, other) -> kam se kam RA_VALIDATED. Scoring me ye 1.0 / 0.75 / 0.5 weight hai.
     */
    static String upgradedValidation(String current, String evidenceType) {
        String target = "ASSESSMENT".equals(evidenceType) ? "ASSESSMENT_VERIFIED" : "RA_VALIDATED";
        int have = current == null ? 0 : Math.max(0, VALIDATION_LADDER.indexOf(current));
        int want = VALIDATION_LADDER.indexOf(target);
        return VALIDATION_LADDER.get(Math.max(have, want));
    }

    private static void validate(EvidenceRequest r) {
        if (!TYPES.contains(r.evidenceType().trim().toUpperCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "evidence_type must be one of " + String.join(", ", TYPES.stream().sorted().toList()));
        }
        if (r.newProficiency() != null && (r.newProficiency() < 1 || r.newProficiency() > 5)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "new_proficiency must be between 1 and 5");
        }
        if (r.score() != null && r.score().signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "score cannot be negative");
        }
        if (r.maximumScore() != null && r.maximumScore().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "maximum_score must be greater than 0");
        }
        if (r.score() != null && r.maximumScore() != null && r.score().compareTo(r.maximumScore()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "score cannot be higher than maximum_score");
        }
        if (r.score() != null && r.maximumScore() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "maximum_score is required when score is given");
        }
        if (r.evidenceDate() != null && r.evidenceDate().isAfter(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "evidence_date cannot be in the future");
        }
        if (r.validUntil() != null && r.evidenceDate() != null && r.validUntil().isBefore(r.evidenceDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "valid_until cannot be before evidence_date");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static Map<String, Object> evidenceMap(SkillEvidence e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidence_id", e.getEvidenceId());
        m.put("employee_skill_id", e.getEmployeeSkillId());
        m.put("learning_recommendation_id", e.getLearningRecommendationId());
        m.put("evidence_type", e.getEvidenceType());
        m.put("evidence_title", e.getEvidenceTitle());
        m.put("evidence_description", e.getEvidenceDescription());
        m.put("score", e.getScore());
        m.put("maximum_score", e.getMaximumScore());
        m.put("evidence_date", e.getEvidenceDate());
        m.put("valid_until", e.getValidUntil());
        m.put("file_reference", e.getFileReference());
        m.put("verification_status", e.getVerificationStatus());
        m.put("verified_by", e.getVerifiedBy());
        m.put("verified_at", e.getVerifiedAt());
        m.put("recorded_by", e.getRecordedBy());
        m.put("created_at", e.getCreatedAt());
        return m;
    }
}

