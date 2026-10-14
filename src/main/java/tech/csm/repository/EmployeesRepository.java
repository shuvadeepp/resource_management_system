package tech.csm.repository;

 
import tech.csm.model.Employees;
import org.springframework.data.jpa.repository.JpaRepository;

import tech.csm.model.Employees;

public interface EmployeesRepository extends JpaRepository<Employees, Integer> {
	
}
 
