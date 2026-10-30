package tech.csm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tech.csm.model.MatchingResult;

@Repository
public interface MatchingResultRepository extends JpaRepository<MatchingResult, Integer> {

    List<MatchingResult> findByMatchingRunIdOrderByRankPositionAsc(Integer matchingRunId);

    List<MatchingResult> findByRecommendationStatus(String recommendationStatus);

    long countByMatchingRunId(Integer matchingRunId);

    @Query("select m.matchCategory, count(m) from MatchingResult m group by m.matchCategory")
    List<Object[]> countByCategory();

    @Query("select m.matchingRunId, count(m) from MatchingResult m where m.matchingRunId in :ids group by m.matchingRunId")
    List<Object[]> countByRunIds(@Param("ids") Collection<Integer> runIds);
}
