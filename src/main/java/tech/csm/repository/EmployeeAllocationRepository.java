package tech.csm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.csm.model.EmployeeAllocation;

@Repository
public interface EmployeeAllocationRepository extends JpaRepository<EmployeeAllocation, Integer> {
}
