package tech.csm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.dto.LearningResourceRequest;
import tech.csm.exception.ApiException;
import tech.csm.model.LearningResource;
import tech.csm.model.Skill;
import tech.csm.repository.LearningResourceRepository;
import tech.csm.repository.SkillRepository;

/**
 * L&K training repository (FR-GAP-05): catalog ka CRUD + approval, aur recommendations ke liye
 * "is gap ke liye kaun si approved resource fit hoti hai" ka logic.
 */
@Service
public class LearningCatalogService {

    public static final Set<String> TYPES = Set.of("COURSE", "CERTIFICATION", "DOCUMENTATION", "MENTORING",
            "PRACTICAL_ASSIGNMENT", "OTHER", "ASSESSMENT");
    public static final Set<String> VALIDATIONS = Set.of("KNOWLEDGE_ASSESSMENT", "PRACTICAL_ASSIGNMENT",
            "DEMONSTRATION", "TECHNICAL_REVIEW", "CERTIFICATE", "NONE");

    private final LearningResourceRepository repository;
    private final SkillRepository skillRepository;
    private final AuditService audit;

    /** Hafte me kitne ghante seekha jaa sakta hai - estimated_weeks isi se nikalta hai. Configurable. */
    @Value("${app.learning.hours-per-week:5}")
    private int hoursPerWeek;

    public LearningCatalogService(LearningResourceRepository repository, SkillRepository skillRepository,
                                  AuditService audit) {
        this.repository = repository;
        this.skillRepository = skillRepository;
        this.audit = audit;
    }

    // ---------------- matching a gap to the catalog ----------------

    /** skill_id -> approved + active resources. Ek query, N gaps ke liye. */
    public Map<Integer, List<LearningResource>> approvedBySkill(Collection<Integer> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Map.of();
        }
        return repository.findUsable(skillIds).stream()
                .collect(Collectors.groupingBy(LearningResource::getSkillId));
    }

    /**
     * Gap ke liye fit resources, sabse kam hours pehle (hours na ho to last). Fit = approved + active,
     * learner ka level from_proficiency se kam na ho, aur to_proficiency required level tak pahunche.
     * Skill hi nahi hai (current null/0) to beginner = level 1 maana jaata hai.
     */
    public static List<LearningResource> fitting(List<LearningResource> candidates, Integer current, int required) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int level = current == null ? 1 : Math.max(1, current);
        return candidates.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsApproved()) && Boolean.TRUE.equals(r.getIsActive()))
                .filter(r -> r.getFromProficiency() != null && r.getToProficiency() != null)
                .filter(r -> r.getFromProficiency() <= level && r.getToProficiency() >= required)
                .sorted(Comparator
                        .comparing(LearningResource::getEstimatedHours, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LearningResource::getLearningResourceId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Sabse behtar fit, ya null. */
    public static LearningResource pick(List<LearningResource> candidates, Integer current, int required) {
        List<LearningResource> fit = fitting(candidates, current, required);
        return fit.isEmpty() ? null : fit.get(0);
    }

    public Integer weeksFor(BigDecimal hours) {
        return weeks(hours, hoursPerWeek);
    }

    static Integer weeks(BigDecimal hours, int hoursPerWeek) {
        if (hours == null || hours.signum() <= 0) {
            return null;
        }
        int perWeek = Math.max(1, hoursPerWeek);
        return Math.max(1, hours.divide(BigDecimal.valueOf(perWeek), 0, RoundingMode.CEILING).intValue());
    }

    // ---------------- catalog CRUD ----------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Integer skillId, Boolean approved, Boolean active, String q,
                                          Map<Integer, Skill> skillsById) {
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase();
        return repository.findAll().stream()
                .filter(r -> skillId == null || skillId.equals(r.getSkillId()))
                .filter(r -> approved == null || approved.equals(r.getIsApproved()))
                .filter(r -> active == null || active.equals(r.getIsActive()))
                .filter(r -> needle == null
                        || (r.getResourceTitle() != null && r.getResourceTitle().toLowerCase().contains(needle))
                        || (r.getProvider() != null && r.getProvider().toLowerCase().contains(needle)))
                .sorted(Comparator.comparing(LearningResource::getSkillId)
                        .thenComparing(LearningResource::getResourceTitle, String.CASE_INSENSITIVE_ORDER))
                .map(r -> toMap(r, skillsById))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Integer id, Map<Integer, Skill> skillsById) {
        return toMap(find(id), skillsById);
    }

    /** Nayi resource hamesha unapproved banti hai - L&K ko alag se approve karni hoti hai. */
    @Transactional
    public Map<String, Object> create(LearningResourceRequest req, Integer userId, Map<Integer, Skill> skillsById) {
        validate(req);
        if (repository.findBySkillIdAndResourceTitle(req.skillId(), req.resourceTitle().trim()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This skill already has a learning resource with the title '" + req.resourceTitle().trim() + "'");
        }
        LearningResource r = new LearningResource();
        apply(r, req);
        r.setIsApproved(false);
        r.setCreatedBy(userId);
        r = repository.save(r);
        audit.log(userId, AuditService.LEARNING_RESOURCE_CREATED, "learning_resources", r.getLearningResourceId(),
                null, snapshot(r), null);
        return toMap(r, skillsById);
    }

    /**
     * Poora replace. Approved resource ka content (skill, title, type, url, levels, hours, validation,
     * provider) badle to approval hat jaati hai - L&K ko dobara approve karni padti hai.
     */
    @Transactional
    public Map<String, Object> update(Integer id, LearningResourceRequest req, Integer userId,
                                      Map<Integer, Skill> skillsById) {
        validate(req);
        LearningResource r = find(id);
        repository.findBySkillIdAndResourceTitle(req.skillId(), req.resourceTitle().trim())
                .filter(other -> !other.getLearningResourceId().equals(id))
                .ifPresent(other -> {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "This skill already has a learning resource with the title '" + req.resourceTitle().trim() + "'");
                });

        Map<String, Object> old = snapshot(r);
        String contentBefore = content(r);
        apply(r, req);
        boolean approvalReset = Boolean.TRUE.equals(r.getIsApproved()) && !contentBefore.equals(content(r));
        if (approvalReset) {
            r.setIsApproved(false);
            r.setApprovedBy(null);
            r.setApprovedAt(null);
        }
        r = repository.save(r);
        audit.log(userId, AuditService.LEARNING_RESOURCE_UPDATED, "learning_resources", id, old, snapshot(r),
                approvalReset ? "Content changed, approval removed" : null);
        Map<String, Object> body = toMap(r, skillsById);
        if (approvalReset) {
            body.put("note", "The content of an approved resource changed, so it is no longer approved. L&K must approve it again.");
        }
        return body;
    }

    @Transactional
    public Map<String, Object> setApproval(Integer id, boolean approved, Integer userId, Map<Integer, Skill> skillsById) {
        LearningResource r = find(id);
        Map<String, Object> old = snapshot(r);
        r.setIsApproved(approved);
        r.setApprovedBy(approved ? userId : null);
        r.setApprovedAt(approved ? MatchingPersistenceService.nowIst() : null);
        r = repository.save(r);
        audit.log(userId, AuditService.LEARNING_RESOURCE_APPROVAL, "learning_resources", id, old, snapshot(r), null);
        return toMap(r, skillsById);
    }

    // ---------------- helpers ----------------

    private LearningResource find(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Learning resource not found: " + id));
    }

    private void validate(LearningResourceRequest req) {
        if (!skillRepository.existsById(req.skillId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "skill_id " + req.skillId() + " is not in the skill master");
        }
        int from = req.fromProficiency() == null ? 1 : req.fromProficiency();
        int to = req.toProficiency();
        if (from < 1 || from > 5 || to < 1 || to > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "from_proficiency and to_proficiency must be between 1 and 5");
        }
        if (from >= to) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "from_proficiency must be lower than to_proficiency");
        }
        if (req.resourceType() != null && !TYPES.contains(req.resourceType().trim().toUpperCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "resource_type must be one of " + String.join(", ", TYPES.stream().sorted().toList()));
        }
        if (req.validationMethod() != null && !VALIDATIONS.contains(req.validationMethod().trim().toUpperCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_method must be one of " + String.join(", ", VALIDATIONS.stream().sorted().toList()));
        }
        if (req.estimatedHours() != null && req.estimatedHours().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "estimated_hours must be greater than 0");
        }
    }

    private static void apply(LearningResource r, LearningResourceRequest req) {
        r.setSkillId(req.skillId());
        r.setResourceTitle(req.resourceTitle().trim());
        r.setResourceType(req.resourceType() == null ? "COURSE" : req.resourceType().trim().toUpperCase());
        r.setProvider(blankToNull(req.provider()));
        r.setResourceUrl(blankToNull(req.resourceUrl()));
        r.setResourceDescription(blankToNull(req.resourceDescription()));
        r.setFromProficiency(req.fromProficiency() == null ? 1 : req.fromProficiency());
        r.setToProficiency(req.toProficiency());
        r.setEstimatedHours(req.estimatedHours() == null ? null : req.estimatedHours().setScale(1, RoundingMode.HALF_UP));
        r.setValidationMethod(req.validationMethod() == null ? "KNOWLEDGE_ASSESSMENT" : req.validationMethod().trim().toUpperCase());
        r.setIsActive(req.isActive() == null ? Boolean.TRUE : req.isActive());
    }

    private static String content(LearningResource r) {
        return String.join("|", String.valueOf(r.getSkillId()), String.valueOf(r.getResourceTitle()),
                String.valueOf(r.getResourceType()), String.valueOf(r.getProvider()), String.valueOf(r.getResourceUrl()),
                String.valueOf(r.getFromProficiency()), String.valueOf(r.getToProficiency()),
                String.valueOf(r.getEstimatedHours()), String.valueOf(r.getValidationMethod()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Map<String, Object> snapshot(LearningResource r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("skill_id", r.getSkillId());
        m.put("resource_title", r.getResourceTitle());
        m.put("resource_type", r.getResourceType());
        m.put("from_proficiency", r.getFromProficiency());
        m.put("to_proficiency", r.getToProficiency());
        m.put("estimated_hours", r.getEstimatedHours());
        m.put("validation_method", r.getValidationMethod());
        m.put("is_approved", r.getIsApproved());
        m.put("is_active", r.getIsActive());
        return m;
    }

    public Map<String, Object> toMap(LearningResource r, Map<Integer, Skill> skillsById) {
        Map<String, Object> m = new LinkedHashMap<>();
        Skill skill = skillsById == null ? null : skillsById.get(r.getSkillId());
        m.put("learning_resource_id", r.getLearningResourceId());
        m.put("skill_id", r.getSkillId());
        m.put("skill_name", skill != null ? skill.getSkillName() : null);
        m.put("resource_title", r.getResourceTitle());
        m.put("resource_type", r.getResourceType());
        m.put("provider", r.getProvider());
        m.put("resource_url", r.getResourceUrl());
        m.put("resource_description", r.getResourceDescription());
        m.put("from_proficiency", r.getFromProficiency());
        m.put("to_proficiency", r.getToProficiency());
        m.put("estimated_hours", r.getEstimatedHours());
        m.put("estimated_weeks", weeksFor(r.getEstimatedHours()));
        m.put("validation_method", r.getValidationMethod());
        m.put("is_approved", r.getIsApproved());
        m.put("approved_by", r.getApprovedBy());
        m.put("approved_at", r.getApprovedAt());
        m.put("is_active", r.getIsActive());
        m.put("created_by", r.getCreatedBy());
        m.put("created_at", r.getCreatedAt());
        m.put("updated_at", r.getUpdatedAt());
        return m;
    }
}
