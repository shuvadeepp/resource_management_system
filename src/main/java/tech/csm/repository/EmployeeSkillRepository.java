package tech.csm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tech.csm.model.EmployeeSkill;

@Repository
public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Integer> {

	List<EmployeeSkill> findByEmployee_employeeIdAndIsActive(Integer empId, Integer isActive);

	// Saare employees ki active skills ek query me (skill master join karke).
	// Caller employees ko pehle usi transaction me load kare, warna har row ka employee alag select hoga.
	@Query("select es from EmployeeSkill es join fetch es.skill where es.employee.employeeId in :ids and es.isActive = 1")
	List<EmployeeSkill> findActiveWithSkillByEmployeeIds(@Param("ids") Collection<Integer> ids);
}
