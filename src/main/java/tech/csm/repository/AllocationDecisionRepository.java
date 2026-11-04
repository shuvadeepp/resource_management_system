package tech.csm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import tech.csm.model.AllocationDecision;

@Repository
public interface AllocationDecisionRepository extends JpaRepository<AllocationDecision, Integer> {

    List<AllocationDecision> findByRequirementIdOrderByDecisionDateDesc(Integer requirementId);

    List<AllocationDecision> findByMatchingResultIdIn(Collection<Integer> matchingResultIds);

    // allocation_id set hai = us match ka allocation confirm ho chuka
    boolean existsByMatchingResultIdAndAllocationIdIsNotNull(Integer matchingResultId);

    List<AllocationDecision> findByAllocationId(Integer allocationId);

    // [AllocationDecision, employee_id] - decision ka employee matching_results se aata hai
    @Query("select d, mr.employeeId from AllocationDecision d, MatchingResult mr"
            + " where mr.matchingResultId = d.matchingResultId and mr.employeeId in :employeeIds")
    List<Object[]> findWithEmployeeIdByEmployeeIdIn(Collection<Integer> employeeIds);

    @Query("select d, mr.employeeId from AllocationDecision d, MatchingResult mr"
            + " where mr.matchingResultId = d.matchingResultId")
    List<Object[]> findAllWithEmployeeId();

    @Query("select d.decision, count(d) from AllocationDecision d group by d.decision")
    List<Object[]> countByDecision();
}
