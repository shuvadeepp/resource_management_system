package tech.csm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.csm.model.EmployeeSkill;

@Repository
public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Integer> {

	List<EmployeeSkill> findByEmployee_employeeIdAndIsActive(Integer empId, Integer isActive);
 
}