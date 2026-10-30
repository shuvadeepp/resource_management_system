package tech.csm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import tech.csm.model.MatchingRun;

@Repository
public interface MatchingRunRepository extends JpaRepository<MatchingRun, Integer> {

    List<MatchingRun> findByRequirementIdOrderByMatchingRunIdDesc(Integer requirementId);

    List<MatchingRun> findByRequirementIdAndRunStatusInOrderByMatchingRunIdDesc(Integer requirementId,
                                                                                Collection<String> statuses);

    List<MatchingRun> findByRunStatusIn(Collection<String> statuses);

    List<MatchingRun> findTop5ByOrderByMatchingRunIdDesc();

    @Query("select r.runStatus, count(r) from MatchingRun r group by r.runStatus")
    List<Object[]> countByStatus();
}
