package tech.csm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tech.csm.model.ResourceAvailability;

@Repository
public interface ResourceAvailabilityRepository extends JpaRepository<ResourceAvailability, Integer> {

	List<ResourceAvailability> findByEmployee_EmployeeIdIn(Collection<Integer> employeeIds);

	// Sirf har employee ki current (max id) row - history rows dobara count na hon
	@Query("select count(ra) from ResourceAvailability ra where ra.availabilityStatus in :statuses"
			+ " and ra.availabilityId = (select max(r2.availabilityId) from ResourceAvailability r2"
			+ " where r2.employee = ra.employee)")
	long countCurrentByAvailabilityStatusIn(@Param("statuses") Collection<String> statuses);
}
