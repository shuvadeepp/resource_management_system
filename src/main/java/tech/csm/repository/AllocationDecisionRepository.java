package tech.csm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.csm.model.AllocationDecision;

@Repository
public interface AllocationDecisionRepository extends JpaRepository<AllocationDecision, Integer> {

    List<AllocationDecision> findByRequirementIdOrderByDecisionDateDesc(Integer requirementId);
}
