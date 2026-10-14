package tech.csm.repository;
 

import tech.csm.model.EmployeeSkill;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Integer> {
	List<EmployeeSkill> findByEmployeeIdAndIsActiveTrue(String string);
}