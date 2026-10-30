package tech.csm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.csm.model.LearningRecommendation;

@Repository
public interface LearningRecommendationRepository extends JpaRepository<LearningRecommendation, Integer> {

    List<LearningRecommendation> findByMatchingResultId(Integer matchingResultId);

    List<LearningRecommendation> findByMatchingResultIdIn(Collection<Integer> matchingResultIds);
}
