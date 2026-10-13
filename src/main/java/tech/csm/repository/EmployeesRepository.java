package tech.csm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import tech.csm.model.Employees;

public interface EmployeesRepository extends JpaRepository<Employees, Integer> {
	
	
}
