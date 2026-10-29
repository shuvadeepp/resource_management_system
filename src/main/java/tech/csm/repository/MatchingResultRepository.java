package tech.csm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.csm.model.MatchingResult;

@Repository
public interface MatchingResultRepository extends JpaRepository<MatchingResult, Integer> {

    List<MatchingResult> findByMatchingRunIdOrderByRankPositionAsc(Integer matchingRunId);

    List<MatchingResult> findByRecommendationStatus(String recommendationStatus);
}
