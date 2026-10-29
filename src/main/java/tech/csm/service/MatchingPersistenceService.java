package tech.csm.service;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.model.MatchingResult;
import tech.csm.model.MatchingRun;
import tech.csm.model.RequiredSkill;
import tech.csm.model.Skill;
import tech.csm.repository.LearningRecommendationRepository;
import tech.csm.repository.MatchingResultRepository;
import tech.csm.repository.MatchingRunRepository;
import tech.csm.repository.RequiredSkillRepository;
import tech.csm.repository.SkillRepository;
import tech.csm.repository.UserRepo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Gemini ka result (JsonNode, RequirementMatchService me pehle se bana hua)
 * ko matching_runs + matching_results + learning_recommendations me save
 * karta hai. Sirf tab call hota hai jab requirement DB me save hui ho
 * (requirementId != null) aur Gemini ne "matches" array diya ho (error nahi).
 *
 * Response shape (RequirementResponse) yahan se bilkul nahi badalta - ye
 * sirf extra, side-effect persistence hai.
 */
@Service
public class MatchingPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(MatchingPersistenceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MatchingRunRepository matchingRunRepository;
    private final MatchingResultRepository matchingResultRepository;
    private final LearningRecommendationRepository learningRecommendationRepository;
    private final RequiredSkillRepository requiredSkillRepository;
    private final SkillRepository skillRepository;
    private final UserRepo userRepo;

    @Value("${gemini.model}")
    private String geminiModel;

    public MatchingPersistenceService(MatchingRunRepository matchingRunRepository,
                                      MatchingResultRepository matchingResultRepository,
                                      LearningRecommendationRepository learningRecommendationRepository,
                                      RequiredSkillRepository requiredSkillRepository,
                                      SkillRepository skillRepository,
                                      UserRepo userRepo) {
        this.matchingRunRepository = matchingRunRepository;
        this.matchingResultRepository = matchingResultRepository;
        this.learningRecommendationRepository = learningRecommendationRepository;
        this.requiredSkillRepository = requiredSkillRepository;
        this.skillRepository = skillRepository;
        this.userRepo = userRepo;
    }

    @Transactional
    public void persist(Integer requirementId, JsonNode llmResult, int candidatePoolSize) {
        if (requirementId == null || llmResult == null || !(llmResult.path("matches") instanceof ArrayNode matches)) {
            return; // requirement DB me save nahi hui, ya Gemini ne error diya - kuch persist nahi karte
        }

        try {
            Integer currentUserId = currentUserId();

            MatchingRun run = new MatchingRun();
            run.setRequirementId(requirementId);
            run.setScoringConfigurationVersion(geminiModel);
            run.setAlgorithmName("gemini");
            run.setModelVersion(geminiModel);
            run.setCandidatePoolSize(candidatePoolSize);
            run.setRunStatus("COMPLETED");
            run.setExecutedBy(currentUserId);
            run = matchingRunRepository.save(run);

            List<RequiredSkill> requiredSkills = requiredSkillRepository.findByRequirementId(requirementId);

            for (int i = 0; i < matches.size(); i++) {
                JsonNode m = matches.get(i);
                Integer employeeId = m.path("employee_id").isMissingNode() ? null : m.path("employee_id").asInt();
                if (employeeId == null) {
                    continue; // employee resolve nahi hua (code se match nahi mila) to row skip
                }

                BigDecimal score = m.has("match_score") ? BigDecimal.valueOf(m.path("match_score").asDouble()) : null;

                MatchingResult mr = new MatchingResult();
                mr.setMatchingRunId(run.getMatchingRunId());
                mr.setEmployeeId(employeeId);
                mr.setOverallScore(score);
                mr.setMatchCategory(categoryFromScore(score));
                mr.setExplanation(m.path("explanation").asString(null));
                mr.setRankPosition(i + 1);

                ObjectNode strengths = MAPPER.createObjectNode();
                strengths.set("matching_skills", m.path("matching_skills"));
                strengths.set("transferable_skills", m.path("transferable_skills"));
                mr.setStrengths(strengths.toString());

                ObjectNode gaps = MAPPER.createObjectNode();
                gaps.set("missing_skills", m.path("missing_skills"));
                gaps.set("below_required_proficiency", m.path("below_required_proficiency"));
                mr.setSkillGaps(gaps.toString());

                mr = matchingResultRepository.save(mr);

                saveLearningSuggestions(mr, m.path("learning_suggestions"), requiredSkills);
            }
        } catch (Exception ex) {
            // Persistence fail hone par bhi Gemini ka response user ko mil chuka hota hai -
            // sirf log karo, caller (RequirementMatchService) ko fail mat karo.
            log.error("Could not persist matching run/results for requirement {}", requirementId, ex);
        }
    }

    private void saveLearningSuggestions(MatchingResult mr, JsonNode suggestions, List<RequiredSkill> requiredSkills) {
        if (suggestions == null || !suggestions.isArray()) {
            return;
        }
        for (JsonNode s : suggestions) {
            String skillName = s.path("skill").asString(null);
            if (skillName == null || skillName.isBlank()) {
                continue;
            }
            Skill skill = skillRepository.findBySkillNameIgnoreCase(skillName.trim()).orElse(null);
            if (skill == null) {
                continue; // skill master me nahi mila, suggestion skip
            }

            Integer targetProficiency = requiredSkills.stream()
                    .filter(rs -> rs.getSkillId().equals(skill.getSkillId()))
                    .map(RequiredSkill::getMinimumProficiency)
                    .findFirst()
                    .orElse(3); // default, jab requirement me ye skill explicitly nahi maangi thi

            tech.csm.model.LearningRecommendation lr = new tech.csm.model.LearningRecommendation();
            lr.setMatchingResultId(mr.getMatchingResultId());
            lr.setSkillId(skill.getSkillId());
            lr.setTargetProficiency(targetProficiency);
            lr.setLearningAction(s.path("action").asString("Review and build this skill"));
            lr.setSkillGapDescription(skillName);
            learningRecommendationRepository.save(lr);
        }
    }

    private String categoryFromScore(BigDecimal score) {
        if (score == null) return "LOW";
        double v = score.doubleValue();
        if (v >= 80) return "EXCELLENT";
        if (v >= 65) return "GOOD";
        if (v >= 50) return "PARTIAL";
        return "LOW";
    }

    private Integer currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) return null;
            return userRepo.findByUserName(auth.getName()).map(u -> u.getUserId()).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }
}
