package tech.csm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.csm.model.EmployeeAllocation;

@Repository
public interface EmployeeAllocationRepository extends JpaRepository<EmployeeAllocation, Integer> {

    List<EmployeeAllocation> findByEmployeeIdInAndAllocationStatusIn(Collection<Integer> employeeIds,
                                                                     Collection<String> statuses);

    List<EmployeeAllocation> findByAllocationStatusIn(Collection<String> statuses);
}
