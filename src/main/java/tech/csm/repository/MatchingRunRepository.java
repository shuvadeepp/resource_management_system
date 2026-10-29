package tech.csm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.csm.model.MatchingRun;

@Repository
public interface MatchingRunRepository extends JpaRepository<MatchingRun, Integer> {

    List<MatchingRun> findByRequirementIdOrderByMatchingRunIdDesc(Integer requirementId);
}
