package tech.csm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.csm.model.ResourceAvailability;

@Repository
public interface ResourceAvailabilityRepository extends JpaRepository<ResourceAvailability, Integer> {

	List<ResourceAvailability> findByEmployee_EmployeeIdIn(Collection<Integer> employeeIds);

	long countByAvailabilityStatusIn(Collection<String> statuses);
}
