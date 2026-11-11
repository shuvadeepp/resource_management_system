package tech.csm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.csm.model.SkillEvidence;

public interface SkillEvidenceRepository extends JpaRepository<SkillEvidence, Integer> {

    List<SkillEvidence> findByLearningRecommendationIdOrderByEvidenceIdDesc(Integer learningRecommendationId);
}
