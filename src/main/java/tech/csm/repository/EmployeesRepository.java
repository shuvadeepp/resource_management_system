package tech.csm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.csm.model.Employees;

public interface EmployeesRepository extends JpaRepository<Employees, Integer> {

	// Ek hi query me designation/department/location bhi - per-row extra selects nahi
	@Query("select e from Employees e left join fetch e.designation left join fetch e.department left join fetch e.location")
	List<Employees> findAllWithRefs();

	@Query("select e from Employees e left join fetch e.designation left join fetch e.department left join fetch e.location"
			+ " where e.employeeId in :ids")
	List<Employees> findAllWithRefsByIdIn(@Param("ids") Collection<Integer> ids);

	// Inhe findAllWithRefs() ke baad USI transaction me chalao: ye un hi Employees objects ki
	// employeeSkill / resourceAvailability lists ek-ek query me bhar dete hain (har employee ki
	// alag lazy query nahi). Dono alag queries hain kyunki do List collections ek saath fetch nahi ho sakti.
	// order by PK = wahi order jo lazy loading me aata tha.
	@Query("select e from Employees e left join fetch e.employeeSkill es left join fetch es.skill"
			+ " where e.employeeId in :ids order by es.employeeSkillId")
	List<Employees> fetchSkills(@Param("ids") Collection<Integer> ids);

	@Query("select e from Employees e left join fetch e.resourceAvailability ra"
			+ " where e.employeeId in :ids order by ra.availabilityId")
	List<Employees> fetchAvailability(@Param("ids") Collection<Integer> ids);
}
